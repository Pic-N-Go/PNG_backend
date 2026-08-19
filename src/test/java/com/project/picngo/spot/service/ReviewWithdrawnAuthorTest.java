package com.project.picngo.spot.service;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.common.image.service.ExifExtractor;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.dto.ReviewListResponse;
import com.project.picngo.spot.repository.ReviewPhotoRepository;
import com.project.picngo.spot.repository.ReviewRepository;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 탈퇴 계정의 닉네임·프로필 사진은 파기(30일)를 기다리지 않고 즉시 가린다.
 * 게시글은 {@code PostAuthorResponse.from}이 가리는데, 리뷰 목록은 그 경로를 타지 않아
 * 같은 규칙을 따로 넣어야 한다 — 빠뜨리면 30일 동안 탈퇴한 사람의 이름과 사진이 노출된다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewWithdrawnAuthorTest {

    @Mock ReviewRepository reviewRepository;
    @Mock ReviewPhotoRepository reviewPhotoRepository;
    @Mock SpotRepository spotRepository;
    @Mock UserRepository userRepository;
    @Mock ImageStorageService imageStorageService;
    @Mock ExifExtractor exifExtractor;

    @InjectMocks ReviewService service;

    private ReviewListResponse.ReviewInfo firstReviewOf(User author) {
        Review review = Review.builder()
                .userId(author.getId())
                .rating(5)
                .content("좋아요")
                .build();
        ReflectionTestUtils.setField(review, "id", 11L);

        when(spotRepository.existsById(anyLong())).thenReturn(true);
        when(reviewRepository.findBySpotId(anyLong(), any())).thenReturn(new PageImpl<>(List.of(review)));
        when(reviewRepository.findAvgAndCountBySpotId(anyLong())).thenReturn(List.of());
        when(reviewRepository.findRatingDistributionBySpotId(anyLong())).thenReturn(List.of());
        when(userRepository.findByIdIn(List.of(author.getId()))).thenReturn(List.of(author));
        when(imageStorageService.getPresignedUrl(anyString())).thenAnswer(i -> i.getArgument(0));

        return service.getReviews(1L, "LATEST", 0, 20).reviews().content().get(0);
    }

    private User author(String nickname) {
        User user = User.createLocalUser("a@b.com", "encoded", nickname, Set.of(SpotCategory.NIGHT_VIEW));
        ReflectionTestUtils.setField(user, "id", 7L);
        user.updateProfileImage("profile/7/mine.jpg");
        return user;
    }

    @Test
    @DisplayName("탈퇴한 작성자의 닉네임과 사진은 리뷰 목록에서 가려진다")
    void masksWithdrawnAuthor() {
        User withdrawn = author("홍길동");
        withdrawn.withdraw(LocalDateTime.now());

        ReviewListResponse.ReviewInfo info = firstReviewOf(withdrawn);

        assertThat(info.nickname()).isEqualTo(User.WITHDRAWN_DISPLAY_NAME);
        assertThat(info.profileImageUrl()).isNull();
        // presign도 하지 않는다 — objectKey가 URL로 새어 나갈 경로 자체를 없앤다.
        verify(imageStorageService, never()).getPresignedUrl("profile/7/mine.jpg");
    }

    @Test
    @DisplayName("살아 있는 작성자는 그대로 보인다")
    void keepsActiveAuthor() {
        ReviewListResponse.ReviewInfo info = firstReviewOf(author("홍길동"));

        assertThat(info.nickname()).isEqualTo("홍길동");
        assertThat(info.profileImageUrl()).isEqualTo("profile/7/mine.jpg");
    }
}
