package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotCategory;
import com.project.picngo.spot.domain.enums.SpotSource;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.domain.enums.TimePeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MyReviewInfoTest {

    @Test
    @DisplayName("썸네일이 있으면 썸네일을 쓴다")
    void usesThumbnailWhenPresent() {
        assertThat(imageUrlOf("https://thumb.jpg", "https://origin.jpg"))
                .isEqualTo("https://thumb.jpg");
    }

    @Test
    @DisplayName("썸네일이 빈 문자열이면 원본 이미지로 폴백한다")
    void fallsBackToImageUrlWhenThumbnailBlank() {
        assertThat(imageUrlOf("", "https://origin.jpg")).isEqualTo("https://origin.jpg");
    }

    @Test
    @DisplayName("둘 다 비어 있으면 null (프론트 그라디언트 폴백 기준)")
    void returnsNullWhenBothBlank() {
        assertThat(imageUrlOf("", "")).isNull();
        assertThat(imageUrlOf(null, null)).isNull();
    }

    private String imageUrlOf(String thumbnailUrl, String imageUrl) {
        Spot spot = Spot.builder()
                .name("광안리")
                .address("부산")
                .latitude(35.153)
                .longitude(129.118)
                .category(SpotCategory.BEACH)
                .source(SpotSource.TOUR_API)
                .status(SpotStatus.APPROVED)
                .thumbnailUrl(thumbnailUrl)
                .imageUrl(imageUrl)
                .build();

        Review review = Review.builder()
                .spot(spot)
                .userId(1L)
                .rating(5)
                .content("좋아요")
                .timePeriod(TimePeriod.SUNSET)
                .build();

        return MyReviewListResponse.MyReviewInfo.of(review, Set.of(), List.of()).spotImageUrl();
    }
}
