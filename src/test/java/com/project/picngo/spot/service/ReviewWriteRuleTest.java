package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ImageErrorCode;
import com.project.picngo.common.image.domain.ExifConsentStatus;
import com.project.picngo.common.image.service.ImageStorageService;
import org.springframework.dao.DataIntegrityViolationException;
import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.TimePeriod;
import com.project.picngo.spot.dto.ReviewCreateRequest;
import com.project.picngo.spot.dto.ReviewUpdateRequest;
import com.project.picngo.spot.repository.ReviewPhotoRepository;
import com.project.picngo.spot.repository.ReviewRepository;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewWriteRuleTest {

    private static final Long USER_ID = 1L;
    private static final Long SPOT_ID = 7L;
    private static final Long REVIEW_ID = 30L;

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewPhotoRepository reviewPhotoRepository;
    @Mock private SpotRepository spotRepository;
    @Mock private UserRepository userRepository;
    @Mock private ImageStorageService imageStorageService;

    @InjectMocks private ReviewService reviewService;

    private static ReviewCreateRequest request(int rating) {
        return new ReviewCreateRequest(rating, "이 스팟 정말 좋았습니다 추천합니다", TimePeriod.SUNSET,
                null, null, LocalDate.of(2026, 7, 20),
                ExifConsentStatus.DECLINED, ExifConsentStatus.DECLINED);
    }

    private static ReviewUpdateRequest updateRequest(int rating) {
        return new ReviewUpdateRequest(rating, "이 스팟 정말 좋았습니다 추천합니다", TimePeriod.SUNSET,
                null, null, LocalDate.of(2026, 7, 20));
    }

    @Test
    @DisplayName("리뷰 작성 시 EXIF 동의 상태가 UNKNOWN이면 거부한다")
    void rejectsUnknownExifConsent() {
        ReviewCreateRequest request = new ReviewCreateRequest(
                5,
                "이 스팟 정말 좋았습니다 추천합니다",
                TimePeriod.SUNSET,
                null,
                null,
                LocalDate.of(2026, 7, 20),
                ExifConsentStatus.UNKNOWN,
                ExifConsentStatus.DECLINED
        );

        CustomException exception = assertThrows(
                CustomException.class,
                () -> reviewService.createReview(USER_ID, SPOT_ID, request, List.of())
        );

        assertEquals(ImageErrorCode.INVALID_EXIF_CONSENT, exception.getErrorCode());
        verifyNoInteractions(spotRepository, reviewRepository, imageStorageService);
    }

    @Test
    @DisplayName("같은 스팟에 이미 리뷰가 있으면 작성이 거부된다 (스팟당 1인 1리뷰)")
    void rejectsSecondReviewOnSameSpot() {
        given(spotRepository.findById(SPOT_ID)).willReturn(Optional.of(mock(Spot.class)));
        given(reviewRepository.existsBySpotIdAndUserId(SPOT_ID, USER_ID)).willReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(USER_ID, SPOT_ID, request(5), List.of()))
                .isInstanceOf(CustomException.class);

        verify(reviewRepository, never()).saveAndFlush(any(Review.class));
    }

    @Test
    @DisplayName("앱 레벨 검사를 통과해도 DB 유니크 제약 위반이면 409로 변환된다 (동시 요청)")
    void convertsUniqueViolationToConflict() {
        given(spotRepository.findById(SPOT_ID)).willReturn(Optional.of(mock(Spot.class)));
        given(reviewRepository.existsBySpotIdAndUserId(SPOT_ID, USER_ID)).willReturn(false);
        given(reviewRepository.saveAndFlush(any(Review.class)))
                .willThrow(new DataIntegrityViolationException("uk_review_spot_user"));

        assertThatThrownBy(() -> reviewService.createReview(USER_ID, SPOT_ID, request(5), List.of()))
                .isInstanceOf(CustomException.class);

        // 위반이 사진 업로드 전에 확정되므로 S3에 올라간 것이 없다
        verify(imageStorageService, never()).delete(anyString());
    }

    @Test
    @DisplayName("남의 리뷰는 단건 조회도 거부된다")
    void rejectsReadingOthersReview() {
        Review notMine = mock(Review.class);
        given(notMine.getUserId()).willReturn(2L);
        given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(notMine));

        assertThatThrownBy(() -> reviewService.getMyReview(USER_ID, REVIEW_ID))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("리뷰 수정 시 스팟 평점·리뷰 수를 다시 계산한다")
    void refreshesSpotStatsOnUpdate() {
        Spot spot = mock(Spot.class);
        given(spot.getId()).willReturn(SPOT_ID);

        Review review = mock(Review.class);
        given(review.getUserId()).willReturn(USER_ID);
        given(review.getSpot()).willReturn(spot);
        given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));
        given(reviewPhotoRepository.findByReview_IdInOrderByIdAsc(List.of(REVIEW_ID))).willReturn(List.of());

        reviewService.updateReview(USER_ID, REVIEW_ID, updateRequest(1));

        // 별점을 바꿨으면 스팟 평균이 다시 계산돼야 한다
        verify(reviewRepository).findAvgAndCountBySpotId(SPOT_ID);
        verify(spot).updateReviewStats(any(), anyInt());
    }
}
