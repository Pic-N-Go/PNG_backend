package com.project.picngo.spot.repository;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotSource;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.domain.enums.TimePeriod;
import com.project.picngo.spot.dto.ReviewedSpotResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
// @DataJpaTest는 기본적으로 데이터소스를 임의의 H2 URL로 갈아치워 application-test.yml의
// MODE=MySQL 설정을 버린다. 그 상태에서는 spot_categories 삽입이 체크 제약에 걸린다.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MyReviewedSpotsTest {

    private static final Long MY_ID = 1L;
    private static final Long OTHER_ID = 2L;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private SpotRepository spotRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("내가 리뷰한 스팟만 핀으로 내려간다")
    void returnsOnlyMyReviewedSpots() {
        saveReview(saveSpot("광안리", 35.153, 129.118), MY_ID);
        saveReview(saveSpot("경복궁", 37.579, 126.977), OTHER_ID);

        List<ReviewedSpotResponse> pins = reviewRepository.findReviewedSpotsByUserId(MY_ID);

        assertThat(pins).extracting(ReviewedSpotResponse::name).containsExactly("광안리");
    }

    @Test
    @DisplayName("리뷰 작성일 내림차순으로 정렬된다")
    void sortsByReviewedAtDesc() {
        Long oldId = saveReview(saveSpot("먼저 쓴 곳", 35.1, 129.1), MY_ID);
        Long newId = saveReview(saveSpot("나중 쓴 곳", 35.2, 129.2), MY_ID);
        // 연속 저장은 createdAt이 같은 값으로 찍힐 수 있어 정렬 검증이 흔들린다. 값을 직접 벌려 둔다.
        // createdAt은 @Column(updatable = false)지만 JPQL 벌크 UPDATE는 그 제약을 타지 않는다.
        setCreatedAt(oldId, LocalDateTime.of(2026, 1, 1, 0, 0));
        setCreatedAt(newId, LocalDateTime.of(2026, 6, 1, 0, 0));

        List<ReviewedSpotResponse> pins = reviewRepository.findReviewedSpotsByUserId(MY_ID);

        assertThat(pins).extracting(ReviewedSpotResponse::name)
                .containsExactly("나중 쓴 곳", "먼저 쓴 곳");
        assertThat(pins.get(0).reviewedAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
    }

    @Test
    @DisplayName("빈 문자열·공백 썸네일은 imageUrl로 폴백한다 (탭·개행은 SQL TRIM 범위 밖)")
    void fallsBackFromBlankThumbnailToImageUrl() {
        // TourAPI는 이미지가 없을 때 null이 아니라 빈 문자열을 주고, 그대로 저장돼 있다.
        saveReview(saveSpot("썸네일없음", 35.1, 129.1, "", "https://example.com/img.jpg"), MY_ID);
        saveReview(saveSpot("둘다없음", 35.2, 129.2, "", ""), MY_ID);
        // 빈 문자열뿐 아니라 공백만 있는 값도 걸러야 /me/reviews(isBlank 기준)와 결과가 같아진다.
        saveReview(saveSpot("공백썸네일", 35.3, 129.3, "  ", "https://example.com/img2.jpg"), MY_ID);
        // 탭·개행은 SQL TRIM 범위 밖이라 그대로 내려간다 — 알고 남겨둔 차이다(리포지토리 주석 참고).
        saveReview(saveSpot("탭썸네일", 35.4, 129.4, "\t", ""), MY_ID);

        List<ReviewedSpotResponse> pins = reviewRepository.findReviewedSpotsByUserId(MY_ID);

        assertThat(pins).extracting(ReviewedSpotResponse::name, ReviewedSpotResponse::imageUrl)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("썸네일없음", "https://example.com/img.jpg"),
                        org.assertj.core.groups.Tuple.tuple("둘다없음", null),
                        org.assertj.core.groups.Tuple.tuple("공백썸네일", "https://example.com/img2.jpg"),
                        org.assertj.core.groups.Tuple.tuple("탭썸네일", "\t")
                );
    }

    @Test
    @DisplayName("카테고리는 스팟별로 묶여 나온다")
    void groupsCategoriesBySpot() {
        Spot beach = saveSpot("광안리", 35.1, 129.1, Set.of(SpotCategory.BEACH, SpotCategory.NIGHT_VIEW));
        Spot palace = saveSpot("경복궁", 37.5, 126.9, Set.of(SpotCategory.HERITAGE));
        saveReview(beach, MY_ID);
        saveReview(palace, MY_ID);

        Map<Long, List<String>> grouped = new HashMap<>();
        for (Object[] row : reviewRepository.findReviewedSpotCategories(MY_ID)) {
            grouped.computeIfAbsent((Long) row[0], k -> new ArrayList<>())
                    .add(((SpotCategory) row[1]).name());
        }
        grouped.values().forEach(Collections::sort);

        assertThat(grouped.get(beach.getId())).containsExactly("BEACH", "NIGHT_VIEW");
        assertThat(grouped.get(palace.getId())).containsExactly("HERITAGE");
    }

    @Test
    @DisplayName("카테고리 조회 쿼리가 실행되고, 카테고리 없는 스팟은 행이 없다 (서비스의 ETC 폴백이 받는 경우)")
    void omitsSpotsWithoutCategories() {
        Spot bare = saveSpot("무분류", 35.1, 129.1, Set.of());
        saveReview(bare, MY_ID);

        assertThat(reviewRepository.findReviewedSpotCategories(MY_ID)).isEmpty();
        // 핀 자체는 정상적으로 내려간다 — 카테고리만 비어 있다.
        assertThat(reviewRepository.findReviewedSpotsByUserId(MY_ID)).hasSize(1);
    }

    @Test
    @DisplayName("카테고리 그룹핑 결과가 Spot.getCategoryNames()와 일치한다")
    void matchesGetCategoryNames() {
        // 즐겨찾기 핀(SpotResponse)이 쓰는 규칙과 어긋나면 프론트가 두 핀을 다르게 그린다.
        Spot spot = saveSpot("광안리", 35.1, 129.1,
                Set.of(SpotCategory.NIGHT_VIEW, SpotCategory.BEACH, SpotCategory.PARK));
        saveReview(spot, MY_ID);

        List<String> fromQuery = new ArrayList<>();
        for (Object[] row : reviewRepository.findReviewedSpotCategories(MY_ID)) {
            fromQuery.add(((SpotCategory) row[1]).name());
        }
        Collections.sort(fromQuery);

        assertThat(fromQuery).isEqualTo(spot.getCategoryNames());
    }

    @Test
    @DisplayName("작성일이 같으면 id 내림차순으로 순서가 고정된다")
    void breaksCreatedAtTieById() {
        Long first = saveReview(saveSpot("먼저", 35.1, 129.1), MY_ID);
        Long second = saveReview(saveSpot("나중", 35.2, 129.2), MY_ID);
        LocalDateTime sameMoment = LocalDateTime.of(2026, 5, 1, 12, 0);
        setCreatedAt(first, sameMoment);
        setCreatedAt(second, sameMoment);

        assertThat(reviewRepository.findReviewedSpotsByUserId(MY_ID))
                .extracting(ReviewedSpotResponse::name)
                .containsExactly("나중", "먼저");
        assertThat(second).isGreaterThan(first);
    }

    @Test
    @DisplayName("리뷰가 없으면 빈 목록을 받는다")
    void returnsEmptyWhenNoReviews() {
        saveReview(saveSpot("남의 스팟", 35.153, 129.118), OTHER_ID);

        assertThat(reviewRepository.findReviewedSpotsByUserId(MY_ID)).isEmpty();
    }

    private void setCreatedAt(Long reviewId, LocalDateTime at) {
        em.flush();
        em.createQuery("UPDATE Review r SET r.createdAt = :at WHERE r.id = :id")
                .setParameter("at", at)
                .setParameter("id", reviewId)
                .executeUpdate();
        em.clear();
    }

    private Spot saveSpot(String name, Double lat, Double lng) {
        return saveSpot(name, lat, lng, "https://example.com/thumb.jpg", null);
    }

    private Spot saveSpot(String name, Double lat, Double lng, Set<SpotCategory> categories) {
        return spotRepository.save(Spot.builder()
                .name(name)
                .address("부산 수영구")
                .latitude(lat)
                .longitude(lng)
                .source(SpotSource.TOUR_API)
                .status(SpotStatus.APPROVED)
                .categories(categories)
                .build());
    }

    private Spot saveSpot(String name, Double lat, Double lng, String thumbnailUrl, String imageUrl) {
        return spotRepository.save(Spot.builder()
                .name(name)
                .address("부산 수영구")
                .latitude(lat)
                .longitude(lng)
                .source(SpotSource.TOUR_API)
                .status(SpotStatus.APPROVED)
                .thumbnailUrl(thumbnailUrl)
                .imageUrl(imageUrl)
                .build());
    }

    // 스팟당 1인 1리뷰 제약이 있어 한 사용자의 리뷰를 여러 건 만들 때는 스팟도 새로 만든다.
    private Long saveReview(Spot spot, Long userId) {
        return reviewRepository.save(Review.builder()
                .spot(spot)
                .userId(userId)
                .rating(4)
                .content("좋아요")
                .timePeriod(TimePeriod.SUNSET)
                .build()).getId();
    }
}
