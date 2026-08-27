package com.project.picngo.common.image.service;

import com.drew.lang.GeoLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExifExtractorTest {

    @Test
    @DisplayName("EXIF 좌표가 0,0이면 위치 정보가 없는 것으로 처리한다")
    void zeroCoordinatesAreNotUsable() {
        assertThat(ExifExtractor.isUsableGeoLocation(new GeoLocation(0.0, 0.0))).isFalse();
    }

    @Test
    @DisplayName("위도나 경도 중 하나만 0인 정상 좌표는 유지한다")
    void coordinatesOnEquatorOrPrimeMeridianAreUsable() {
        assertThat(ExifExtractor.isUsableGeoLocation(new GeoLocation(0.0, 127.0))).isTrue();
        assertThat(ExifExtractor.isUsableGeoLocation(new GeoLocation(37.0, 0.0))).isTrue();
    }

    @Test
    @DisplayName("EXIF 좌표가 없으면 위치 정보가 없는 것으로 처리한다")
    void missingCoordinatesAreNotUsable() {
        assertThat(ExifExtractor.isUsableGeoLocation(null)).isFalse();
    }
}
