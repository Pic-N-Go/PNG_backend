package com.project.picngo.spot.repository;

import com.project.picngo.common.image.domain.ExifConsentStatus;
import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotSource;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.domain.enums.TimePeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MyReviewQueryTest {

    private static final Long MY_ID = 1L;
    private static final Long OTHER_ID = 2L;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private SpotRepository spotRepository;

    private Spot spot;

    @BeforeEach
    void setUp() {
        spot = spotRepository.save(Spot.builder()
                .name("광안리")
                .address("부산")
                .latitude(35.153)
                .longitude(129.118)
                .source(SpotSource.TOUR_API)
                .status(SpotStatus.APPROVED)
                .thumbnailUrl("https://example.com/thumb.jpg")
                .build());
    }

    @Test
    @DisplayName("내 리뷰만 조회되고 spot이 함께 로딩된다")
    void findsOnlyMyReviewsWithSpot() {
        saveReview(MY_ID, 5);
        saveReview(OTHER_ID, 5);

        Page<Review> page = reviewRepository.findByUserIdWithSpot(MY_ID, PageRequest.of(0, 10, sortByRatingDescThenId()));

        assertThat(page.getTotalElements()).isEqualTo(1);
        // fetch join이 빠지면 여기서 LazyInitializationException이 난다
        assertThat(page.getContent().get(0).getSpot().getName()).isEqualTo("광안리");
    }

    @Test
    @DisplayName("리뷰가 없는 사용자는 빈 페이지를 받는다")
    void returnsEmptyPageWhenNoReviews() {
        saveReview(OTHER_ID, 4);

        Page<Review> page = reviewRepository.findByUserIdWithSpot(MY_ID, PageRequest.of(0, 10, sortByRatingDescThenId()));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("별점이 모두 같아도 id 타이브레이커로 페이지 간 중복·누락이 없다")
    void pagesDoNotOverlapWhenRatingsTie() {
        for (int i = 0; i < 6; i++) {
            saveReviewOnNewSpot(MY_ID, 5);
        }

        Sort sort = sortByRatingDescThenId();
        List<Long> paged = new ArrayList<>();
        paged.addAll(idsOf(reviewRepository.findByUserIdWithSpot(MY_ID, PageRequest.of(0, 3, sort))));
        paged.addAll(idsOf(reviewRepository.findByUserIdWithSpot(MY_ID, PageRequest.of(1, 3, sort))));

        assertThat(paged).hasSize(6).doesNotHaveDuplicates();
        assertThat(paged).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    @DisplayName("LATEST 정렬도 fetch join 쿼리에서 동작한다 (createdAt은 BaseTimeEntity 상속 필드)")
    void latestSortWorksOnFetchJoinQuery() {
        saveReviewOnNewSpot(MY_ID, 3);
        saveReviewOnNewSpot(MY_ID, 4);

        Page<Review> page = reviewRepository.findByUserIdWithSpot(
                MY_ID, PageRequest.of(0, 10, latestSort()));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    // ReviewService.toSort와 같은 정렬. toSort 자체는 같은 패키지의 ReviewSortTest가 검증한다.
    private Sort sortByRatingDescThenId() {
        return Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "id"));
    }

    // createdAt은 BaseTimeEntity 상속 필드라 fetch join 쿼리에서 해석되는지 확인이 필요하다.
    private Sort latestSort() {
        return Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
    }

    private List<Long> idsOf(Page<Review> page) {
        return page.getContent().stream().map(Review::getId).toList();
    }

    // 스팟당 1인 1리뷰 제약이 있어, 한 사용자의 리뷰를 여러 건 만들 때는 스팟도 새로 만든다.
    private void saveReviewOnNewSpot(Long userId, int rating) {
        Spot another = spotRepository.save(Spot.builder()
                .name("스팟" + userId + "-" + rating + "-" + System.nanoTime())
                .address("부산")
                .latitude(35.153)
                .longitude(129.118)
                .source(SpotSource.TOUR_API)
                .status(SpotStatus.APPROVED)
                .build());
        reviewRepository.save(Review.builder()
                .spot(another)
                .userId(userId)
                .rating(rating)
                .content("좋아요")
                .timePeriod(TimePeriod.SUNSET)
                .technicalExifConsent(ExifConsentStatus.UNKNOWN)
                .locationExifConsent(ExifConsentStatus.UNKNOWN)
                .build());
    }

    private void saveReview(Long userId, int rating) {
        reviewRepository.save(Review.builder()
                .spot(spot)
                .userId(userId)
                .rating(rating)
                .content("좋아요")
                .timePeriod(TimePeriod.SUNSET)
                .technicalExifConsent(ExifConsentStatus.UNKNOWN)
                .locationExifConsent(ExifConsentStatus.UNKNOWN)
                .build());
    }
}
