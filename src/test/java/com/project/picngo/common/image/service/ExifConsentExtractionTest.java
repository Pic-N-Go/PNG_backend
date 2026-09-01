package com.project.picngo.common.image.service;

import com.project.picngo.common.image.domain.ExifConsentStatus;
import com.project.picngo.external.KakaoAddressClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ExifConsentExtractionTest {

    private final KakaoAddressClient kakaoAddressClient = mock(KakaoAddressClient.class);
    private final ExifExtractor extractor = new ExifExtractor(kakaoAddressClient);

    private static MultipartFile load(String name) throws IOException {
        try (InputStream inputStream = ExifConsentExtractionTest.class.getResourceAsStream("/exif/" + name)) {
            assertThat(inputStream).as("픽스처 %s", name).isNotNull();
            return new MockMultipartFile("photo", name, "image/jpeg", inputStream.readAllBytes());
        }
    }

    @Test
    @DisplayName("기술 EXIF만 동의하면 촬영정보를 추출하고 위치 API는 호출하지 않는다")
    void extractsOnlyTechnicalExif() throws IOException {
        var exif = extractor.extract(
                load("shot-no-offset.jpg"),
                ExifConsentStatus.GRANTED,
                ExifConsentStatus.DECLINED
        );

        assertThat(exif.takenAt()).isEqualTo(LocalDateTime.of(2026, 8, 23, 5, 32));
        assertThat(exif.latitude()).isNull();
        assertThat(exif.longitude()).isNull();
        assertThat(exif.address()).isNull();
        verifyNoInteractions(kakaoAddressClient);
    }

    @Test
    @DisplayName("둘 다 거절하면 EXIF 값을 추출하지 않는다")
    void skipsExifWhenBothAreDeclined() throws IOException {
        var exif = extractor.extract(
                load("shot-no-offset.jpg"),
                ExifConsentStatus.DECLINED,
                ExifConsentStatus.DECLINED
        );

        assertThat(exif.takenAt()).isNull();
        assertThat(exif.cameraMake()).isNull();
        assertThat(exif.latitude()).isNull();
        assertThat(exif.imageWidth()).isNull();
        assertThat(exif.fileName()).isEqualTo("shot-no-offset.jpg");
        assertThat(exif.fileSize()).isPositive();
        verifyNoInteractions(kakaoAddressClient);
    }

    @Test
    @DisplayName("위치 EXIF만 동의하면 기술 EXIF를 저장하지 않는다")
    void extractsOnlyLocationExif() throws IOException {
        var exif = extractor.extract(
                load("shot-no-offset.jpg"),
                ExifConsentStatus.DECLINED,
                ExifConsentStatus.GRANTED
        );

        assertThat(exif.takenAt()).isNull();
        assertThat(exif.cameraMake()).isNull();
        assertThat(exif.iso()).isNull();
        assertThat(exif.imageWidth()).isNull();
        verifyNoInteractions(kakaoAddressClient);
    }
}
