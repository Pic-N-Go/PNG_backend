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
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
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
    @DisplayName("썸네일이 빈 문자열이면 imageUrl로 폴백하고, 둘 다 비어 있으면 null이 된다")
    void fallsBackFromBlankThumbnailToImageUrl() {
        // TourAPI는 이미지가 없을 때 null이 아니라 빈 문자열을 주고, 그대로 저장돼 있다.
        saveReview(saveSpot("썸네일없음", 35.1, 129.1, "", "https://example.com/img.jpg"), MY_ID);
        saveReview(saveSpot("둘다없음", 35.2, 129.2, "", ""), MY_ID);

        List<ReviewedSpotResponse> pins = reviewRepository.findReviewedSpotsByUserId(MY_ID);

        assertThat(pins).extracting(ReviewedSpotResponse::name, ReviewedSpotResponse::imageUrl)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("썸네일없음", "https://example.com/img.jpg"),
                        org.assertj.core.groups.Tuple.tuple("둘다없음", null)
                );
    }

    // 카테고리가 실제로 담긴 경우는 여기서 검증할 수 없다. H2가 만든 DDL에서는 spot_categories에
    // 어떤 값도 넣지 못한다 — 리터럴 native INSERT('PARK')조차 체크 제약에 걸린다. spot_categories와
    // user_spot_categories가 같은 category 컬럼·같은 enum 체크를 갖는 탓으로 보인다.
    // 운영 스키마(V1__baseline_schema.sql:501)는 varchar(50) + 이름 14개 체크로 정상이다.
    // 기존 테스트들이 Set.of()만 넣거나 flush를 하지 않아 이 한계가 드러나지 않았다.
    // 그룹핑·정렬·ETC 폴백은 ReviewService.getReviewedSpots가 맡고, 실제 MySQL에서 확인해야 한다.

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
