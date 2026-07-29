package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewPhotoCountTest {

    private static MultipartFile file(String name) {
        return new MockMultipartFile("photos", name, "image/jpeg", new byte[]{1});
    }

    private static MultipartFile emptyFile() {
        return new MockMultipartFile("photos", "empty.jpg", "image/jpeg", new byte[0]);
    }

    @Test
    @DisplayName("빈 파트와 null은 세지 않는다 (업로드에서 건너뛰는 기준과 동일)")
    void countsOnlyUploadableParts() {
        assertThat(ReviewService.countUploadable(null)).isZero();
        assertThat(ReviewService.countUploadable(List.of())).isZero();
        assertThat(ReviewService.countUploadable(Arrays.asList(file("a.jpg"), null, emptyFile()))).isEqualTo(1);
    }

    @Test
    @DisplayName("기존 사진 + 신규 파일 합계가 5장을 넘으면 400")
    void rejectsWhenTotalExceedsLimit() {
        // 기존 3장 + 신규 3장 = 6장
        List<MultipartFile> three = List.of(file("a.jpg"), file("b.jpg"), file("c.jpg"));

        assertThatThrownBy(() -> ReviewService.validatePhotoCount(3, three))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("합계가 정확히 5장이면 통과한다")
    void allowsExactlyFive() {
        assertThatCode(() -> ReviewService.validatePhotoCount(3, List.of(file("a.jpg"), file("b.jpg"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> ReviewService.validatePhotoCount(5, List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("빈 파트만 보내면 기존이 5장이어도 통과한다")
    void emptyPartsDoNotCountTowardLimit() {
        assertThatCode(() -> ReviewService.validatePhotoCount(5, List.of(emptyFile())))
                .doesNotThrowAnyException();
    }
}
