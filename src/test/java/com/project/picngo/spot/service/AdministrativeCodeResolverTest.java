package com.project.picngo.spot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdministrativeCodeResolverTest {

    private AdministrativeCodeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AdministrativeCodeResolver();
    }

    @Test
    @DisplayName("충청남도 태안군 주소 해석 테스트")
    void resolveTaean() {
        Optional<AdministrativeCodeResolver.AdministrativeCode> result =
                resolver.resolve("충청남도 태안군 안면읍 꽃지해안로 400");

        assertThat(result).isPresent();
        assertThat(result.get().areaCd()).isEqualTo("44");
        assertThat(result.get().signguCd()).isEqualTo("44825");
    }

    @Test
    @DisplayName("서울특별시 종로구 주소 해석 테스트")
    void resolveJongno() {
        Optional<AdministrativeCodeResolver.AdministrativeCode> result =
                resolver.resolve("서울특별시 종로구 사직로 161");

        assertThat(result).isPresent();
        assertThat(result.get().areaCd()).isEqualTo("11");
        assertThat(result.get().signguCd()).isEqualTo("11110");
    }

    @Test
    @DisplayName("제주특별자치도 서귀포시 주소 해석 테스트")
    void resolveJeju() {
        Optional<AdministrativeCodeResolver.AdministrativeCode> result =
                resolver.resolve("제주특별자치도 서귀포시 성산읍 성산리 1");

        assertThat(result).isPresent();
        assertThat(result.get().areaCd()).isEqualTo("50");
        assertThat(result.get().signguCd()).isEqualTo("50130");
    }

    @Test
    @DisplayName("주소가 null이거나 빈 문자열일 때 빈 Optional 반환")
    void resolveEmptyAddress() {
        assertThat(resolver.resolve(null)).isEmpty();
        assertThat(resolver.resolve("")).isEmpty();
        assertThat(resolver.resolve("   ")).isEmpty();
    }
}
