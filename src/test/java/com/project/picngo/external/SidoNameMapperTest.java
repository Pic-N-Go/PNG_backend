package com.project.picngo.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SidoNameMapperTest {

    @Test
    @DisplayName("도 단위 이름은 축약형으로 정규화된다")
    void normalizesProvinceNames() {
        assertThat(SidoNameMapper.normalize("충청북도")).isEqualTo("충북");
        assertThat(SidoNameMapper.normalize("충청남도")).isEqualTo("충남");
        assertThat(SidoNameMapper.normalize("전라북도")).isEqualTo("전북");
        assertThat(SidoNameMapper.normalize("전라남도")).isEqualTo("전남");
        assertThat(SidoNameMapper.normalize("경상북도")).isEqualTo("경북");
        assertThat(SidoNameMapper.normalize("경상남도")).isEqualTo("경남");
    }

    @Test
    @DisplayName("특별시/광역시/특별자치도는 앞 2글자로 정규화된다")
    void normalizesMetroAndSpecial() {
        assertThat(SidoNameMapper.normalize("서울특별시")).isEqualTo("서울");
        assertThat(SidoNameMapper.normalize("부산광역시")).isEqualTo("부산");
        assertThat(SidoNameMapper.normalize("경기도")).isEqualTo("경기");
        assertThat(SidoNameMapper.normalize("강원특별자치도")).isEqualTo("강원");
        assertThat(SidoNameMapper.normalize("제주특별자치도")).isEqualTo("제주");
        assertThat(SidoNameMapper.normalize("세종특별자치시")).isEqualTo("세종");
        assertThat(SidoNameMapper.normalize("전북특별자치도")).isEqualTo("전북");
    }

    @Test
    @DisplayName("null 또는 2글자 미만이면 null")
    void nullOrTooShort() {
        assertThat(SidoNameMapper.normalize(null)).isNull();
        assertThat(SidoNameMapper.normalize("서")).isNull();
    }
}
