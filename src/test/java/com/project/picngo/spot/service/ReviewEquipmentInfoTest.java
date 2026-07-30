package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewEquipmentInfoTest {

    @Test
    @DisplayName("여러 장비는 ', '로 합쳐 저장한다")
    void joinsWithComma() {
        assertThat(ReviewService.joinEquipmentInfo(List.of("Sony A7IV", "35mm f1.8")))
                .isEqualTo("Sony A7IV, 35mm f1.8");
    }

    @Test
    @DisplayName("값이 없거나 공백뿐이면 null")
    void nullWhenEmpty() {
        assertThat(ReviewService.joinEquipmentInfo(null)).isNull();
        assertThat(ReviewService.joinEquipmentInfo(List.of())).isNull();
        assertThat(ReviewService.joinEquipmentInfo(Arrays.asList("  ", null))).isNull();
    }

    @Test
    @DisplayName("합친 길이가 컬럼 한계(100자)를 넘으면 500이 아니라 400")
    void rejectsWhenJoinedTooLong() {
        // 25자 5개 + 구분자 8자 = 133자
        List<String> tooLong = List.of("a".repeat(25), "b".repeat(25), "c".repeat(25),
                "d".repeat(25), "e".repeat(25));

        assertThatThrownBy(() -> ReviewService.joinEquipmentInfo(tooLong))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("정확히 100자는 통과한다")
    void allowsExactly100() {
        assertThat(ReviewService.joinEquipmentInfo(List.of("a".repeat(100)))).hasSize(100);
    }
}
