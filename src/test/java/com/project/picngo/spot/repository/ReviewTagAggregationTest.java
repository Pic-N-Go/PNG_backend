package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.ReviewTag;
import com.project.picngo.spot.domain.enums.SpotSource;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.domain.enums.TimePeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ReviewTagAggregationTest {

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
                .build());
    }

    @Test
    @DisplayName("2회 이상 쓰인 태그만, 많이 쓰인 순으로 조회된다")
    void returnsOnlyTagsUsedAtLeastTwice() {
        saveReview(1L, Set.of(ReviewTag.NIGHT_VIEW, ReviewTag.BEST_SHOT, ReviewTag.MOODY));
        saveReview(2L, Set.of(ReviewTag.NIGHT_VIEW, ReviewTag.BEST_SHOT));
        saveReview(3L, Set.of(ReviewTag.NIGHT_VIEW));
        // MOODY는 1회뿐이라 제외, TRIPOD_NEEDED는 아무도 안 씀
        saveReview(4L, Set.of(ReviewTag.EASY_PARKING));
        saveReview(5L, Set.of(ReviewTag.EASY_PARKING));

        List<ReviewTag> tags = reviewRepository.findFrequentTagsBySpotId(spot.getId()).stream()
                .map(row -> (ReviewTag) row[0])
                .toList();

        assertThat(tags).containsExactly(ReviewTag.NIGHT_VIEW, ReviewTag.BEST_SHOT, ReviewTag.EASY_PARKING)
                .doesNotContain(ReviewTag.MOODY, ReviewTag.TRIPOD_NEEDED);
    }

    @Test
    @DisplayName("태그가 없거나 전부 1회면 빈 목록")
    void returnsEmptyWhenNothingRepeats() {
        saveReview(1L, Set.of(ReviewTag.MOODY));
        saveReview(2L, Set.of());

        assertThat(reviewRepository.findFrequentTagsBySpotId(spot.getId())).isEmpty();
    }

    private void saveReview(Long userId, Set<ReviewTag> tags) {
        reviewRepository.save(Review.builder()
                .spot(spot)
                .userId(userId)
                .rating(5)
                .content("좋아요")
                .timePeriod(TimePeriod.SUNSET)
                .tags(tags)
                .build());
    }
}
