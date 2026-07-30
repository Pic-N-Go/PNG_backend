package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewSortTest {

    @Test
    @DisplayName("모든 정렬에 id DESC 타이브레이커가 붙는다")
    void everySortHasIdTieBreaker() {
        Sort idDesc = Sort.by(Sort.Direction.DESC, "id");

        assertThat(ReviewService.toSort("LATEST"))
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt").and(idDesc));
        assertThat(ReviewService.toSort("RATING_HIGH"))
                .isEqualTo(Sort.by(Sort.Direction.DESC, "rating").and(idDesc));
        assertThat(ReviewService.toSort("RATING_LOW"))
                .isEqualTo(Sort.by(Sort.Direction.ASC, "rating").and(idDesc));
    }

    @Test
    @DisplayName("허용되지 않은 sort 값은 예외를 던진다")
    void rejectsUnknownSort() {
        assertThatThrownBy(() -> ReviewService.toSort("BEST"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("범위 밖 page/size는 500이 아니라 보정된다")
    void clampsPageAndSize() {
        assertThat(ReviewService.toPageable("LATEST", 0, 0).getPageSize()).isEqualTo(1);
        assertThat(ReviewService.toPageable("LATEST", 0, 500).getPageSize()).isEqualTo(100);
        assertThat(ReviewService.toPageable("LATEST", -1, 20).getPageNumber()).isZero();

        Pageable normal = ReviewService.toPageable("LATEST", 2, 20);
        assertThat(normal.getPageNumber()).isEqualTo(2);
        assertThat(normal.getPageSize()).isEqualTo(20);
    }
}
