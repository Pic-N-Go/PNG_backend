package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.image.service.ExifExtractor;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.domain.ReviewPhoto;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 다른 리뷰의 photoId를 막는 검사는 findMyReview와 중복처럼 보여 지워지기 쉽다. 그 선을 고정한다.
@ExtendWith(MockitoExtension.class)
class ReviewPhotoDeleteTest {

    private static final Long MY_ID = 1L;
    private static final Long MY_REVIEW_ID = 10L;
    private static final Long OTHER_REVIEW_ID = 20L;
    private static final Long PHOTO_ID = 99L;

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewPhotoRepository reviewPhotoRepository;
    @Mock private SpotRepository spotRepository;
    @Mock private UserRepository userRepository;
    @Mock private ImageStorageService imageStorageService;
    @Mock private ExifExtractor exifExtractor;

    @InjectMocks private ReviewService reviewService;

    @Test
    @DisplayName("다른 리뷰의 사진 id를 넘기면 404이고 S3 삭제도 하지 않는다")
    void rejectsPhotoFromAnotherReview() {
        Review myReview = mock(Review.class);
        given(myReview.getUserId()).willReturn(MY_ID);
        given(reviewRepository.findById(MY_REVIEW_ID)).willReturn(Optional.of(myReview));

        Review otherReview = mock(Review.class);
        given(otherReview.getId()).willReturn(OTHER_REVIEW_ID);
        ReviewPhoto otherPhoto = mock(ReviewPhoto.class);
        given(otherPhoto.getReview()).willReturn(otherReview);
        given(reviewPhotoRepository.findById(PHOTO_ID)).willReturn(Optional.of(otherPhoto));

        assertThatThrownBy(() -> reviewService.deleteReviewPhoto(MY_ID, MY_REVIEW_ID, PHOTO_ID))
                .isInstanceOf(CustomException.class);

        verify(reviewPhotoRepository, never()).delete(otherPhoto);
        verify(imageStorageService, never()).delete(anyString());
    }

    @Test
    @DisplayName("남의 리뷰면 사진 조회조차 하지 않는다")
    void rejectsWhenReviewIsNotMine() {
        Review notMine = mock(Review.class);
        given(notMine.getUserId()).willReturn(2L);
        given(reviewRepository.findById(MY_REVIEW_ID)).willReturn(Optional.of(notMine));

        assertThatThrownBy(() -> reviewService.deleteReviewPhoto(MY_ID, MY_REVIEW_ID, PHOTO_ID))
                .isInstanceOf(CustomException.class);

        verify(reviewPhotoRepository, never()).findById(PHOTO_ID);
        verify(imageStorageService, never()).delete(anyString());
    }
}
