package com.project.picngo.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegalDongMapperTest {

    @Test
    @DisplayName("TourAPI areaCode와 법정동 시도 코드(lDongRegnCd) 양방향 매핑 검증")
    void bidirectionalMapping() {
        // 서울: areaCode 1 <-> lDongRegnCd 11
        assertThat(LegalDongMapper.toLdongRegnCd(1)).isEqualTo(11);
        assertThat(LegalDongMapper.toAreaCode(11)).isEqualTo(1);
        assertThat(LegalDongMapper.getRegionName(11)).isEqualTo("서울특별시");

        // 부산: areaCode 6 <-> lDongRegnCd 26
        assertThat(LegalDongMapper.toLdongRegnCd(6)).isEqualTo(26);
        assertThat(LegalDongMapper.toAreaCode(26)).isEqualTo(6);
        assertThat(LegalDongMapper.getRegionName(26)).isEqualTo("부산광역시");

        // 제주: areaCode 39 <-> lDongRegnCd 50
        assertThat(LegalDongMapper.toLdongRegnCd(39)).isEqualTo(50);
        assertThat(LegalDongMapper.toAreaCode(50)).isEqualTo(39);
        assertThat(LegalDongMapper.getRegionName(50)).isEqualTo("제주특별자치도");
    }

    @Test
    @DisplayName("전국 17개 지역 코드가 전부 등록되어 있는지 검증")
    void allSeventeenRegionsRegistered() {
        List<Integer> codes = LegalDongMapper.getAllLdongRegnCodes();
        assertThat(codes).hasSize(17);
    }

    @Test
    @DisplayName("구 법정동 코드 호환성 검증 (강원 42, 전북 45)")
    void legacyCodeCompatibility() {
        assertThat(LegalDongMapper.toAreaCode(42)).isEqualTo(32);
        assertThat(LegalDongMapper.toAreaCode(45)).isEqualTo(35);
    }
}
