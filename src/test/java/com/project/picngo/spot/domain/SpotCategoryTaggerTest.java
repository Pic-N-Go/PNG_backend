package com.project.picngo.spot.domain;

import com.project.picngo.common.domain.SpotCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SpotCategoryTaggerTest {

    @Test
    @DisplayName("cat3(장소형)과 키워드(장면형)가 함께 붙는다")
    void tagsPlaceAndScene() {
        // 해수욕장(cat3) + overview "일몰" → BEACH, SUNRISE_SUNSET 둘 다
        Set<SpotCategory> result = SpotCategoryTagger.tag("A01011200", "협재해수욕장", "일몰이 아름다운 곳");
        assertThat(result).contains(SpotCategory.BEACH, SpotCategory.SUNRISE_SUNSET);
    }

    @Test
    @DisplayName("cat3가 HERITAGE로 매핑된다")
    void tagsHeritage() {
        assertThat(SpotCategoryTagger.tag("A02010800", "불국사", "천년 고찰"))
                .containsExactly(SpotCategory.HERITAGE);
    }

    @Test
    @DisplayName("아무 규칙에도 안 걸리면 ETC")
    void fallbackToEtc() {
        assertThat(SpotCategoryTagger.tag("A02030100", "농촌체험마을", "감자 캐기 체험"))
                .containsExactly(SpotCategory.ETC);
    }

    @Test
    @DisplayName("CITY는 골목/야시장 등만, '시내'는 무시(오탐 방지)")
    void cityExcludesSinae() {
        assertThat(SpotCategoryTagger.tag(null, "남산", "서울 시내를 내려다볼 수 있는 산"))
                .doesNotContain(SpotCategory.CITY);
        assertThat(SpotCategoryTagger.tag(null, "경리단길", "골목 카페가 많은 거리"))
                .contains(SpotCategory.CITY);
    }
}
