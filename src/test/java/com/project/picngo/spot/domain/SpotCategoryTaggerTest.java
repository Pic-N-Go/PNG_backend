package com.project.picngo.spot.domain;

import com.project.picngo.common.domain.SpotCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SpotCategoryTaggerTest {

    @Test
    @DisplayName("TourAPI 4.4 신규 소분류 코드(lclsSystm3) 매핑 테스트")
    void testModernLclsSystm3Mapping() {
        // 공원 (VE030400: 근린공원)
        assertThat(SpotCategoryTagger.tag("VE030400", "올림픽공원", ""))
                .contains(SpotCategory.PARK);

        // 바다 (NA020900: 해변/해수욕장)
        assertThat(SpotCategoryTagger.tag("NA020900", "광안리", ""))
                .contains(SpotCategory.BEACH);

        // 산 (NA010100: 산/고개)
        assertThat(SpotCategoryTagger.tag("NA010100", "지리산", ""))
                .contains(SpotCategory.MOUNTAIN);

        // 숲 (NA040600: 자연휴양림)
        assertThat(SpotCategoryTagger.tag("NA040600", "장태산휴양림", ""))
                .contains(SpotCategory.FOREST);

        // 한옥 (HS010100: 고궁)
        assertThat(SpotCategoryTagger.tag("HS010100", "경복궁", ""))
                .contains(SpotCategory.HANOK);

        // 유적지/미술관 (VE070600: 미술관/화랑)
        assertThat(SpotCategoryTagger.tag("VE070600", "국립현대미술관", ""))
                .contains(SpotCategory.HERITAGE);

        // 카페 (FD050100: 카페)
        assertThat(SpotCategoryTagger.tag("FD050100", "웨이브온", ""))
                .contains(SpotCategory.CAFE);

        // 도시/골목 (VE040100: 골목길, 문화거리)
        assertThat(SpotCategoryTagger.tag("VE040100", "익선동 한옥거리", ""))
                .contains(SpotCategory.CITY);

        // 전망대/야경 (VE010200: 타워/전망대)
        assertThat(SpotCategoryTagger.tag("VE010200", "N서울타워", ""))
                .contains(SpotCategory.NIGHT_VIEW);

        // 축제 (EV010100: 문화관광축제)
        assertThat(SpotCategoryTagger.tag("EV010100", "진해군항제", ""))
                .contains(SpotCategory.FESTIVAL);
    }

    @Test
    @DisplayName("구버전 cat3 코드 호환성 테스트")
    void testLegacyCat3Mapping() {
        // 공원 (A02020700)
        assertThat(SpotCategoryTagger.tag("A02020700", "시민공원", ""))
                .contains(SpotCategory.PARK);

        // 해수욕장 (A01011200)
        assertThat(SpotCategoryTagger.tag("A01011200", "해운대", ""))
                .contains(SpotCategory.BEACH);

        // 산 (A01010400)
        assertThat(SpotCategoryTagger.tag("A01010400", "설악산", ""))
                .contains(SpotCategory.MOUNTAIN);
    }

    @Test
    @DisplayName("키워드 기반 2차 장면형 테마 복합 매핑 테스트")
    void testKeywordRulesCombined() {
        // 소분류는 공원(VE030400)이지만 본문에 '야경'과 '벚꽃'이 있으면 복합 태깅됨
        Set<SpotCategory> tags = SpotCategoryTagger.tag("VE030400", "달빛공원", "밤이 되면 화려한 야경과 봄철 벚꽃이 아름답습니다.");

        assertThat(tags).contains(SpotCategory.PARK, SpotCategory.NIGHT_VIEW, SpotCategory.FLOWER);
    }

    @Test
    @DisplayName("소개글에 단순 축제 언급이 있어도 관광특구는 FESTIVAL로 오태깅되지 않는다")
    void testFestivalNotTaggedForGenericMentionsInOverview() {
        Set<SpotCategory> tags = SpotCategoryTagger.tag("VE040200", "용두산 자갈치 관광특구",
                "용두산·자갈치 관광특구는 부산의 원도심으로 매년 부산자갈치축제와 크리스마스트리문화축제가 열리는 곳이다.");

        assertThat(tags).contains(SpotCategory.CITY);
        assertThat(tags).doesNotContain(SpotCategory.FESTIVAL);
    }

    @Test
    @DisplayName("박물관 소개글에 1층 카페 편의시설이 언급되어도 CAFE로 오태깅되지 않는다")
    void testCafeNotTaggedForFacilityMentionsInMuseum() {
        Set<SpotCategory> tags = SpotCategoryTagger.tag("VE070100", "국립박물관",
                "1층 로비에는 관람객을 위한 카페와 편의시설, 기념품 매장이 있습니다.");

        assertThat(tags).contains(SpotCategory.HERITAGE);
        assertThat(tags).doesNotContain(SpotCategory.CAFE);
    }

    @Test
    @DisplayName("일출/일몰 동의어(해돋이, 낙조) 및 꽃 명소 키워드 정상 태깅 검증")
    void testSunriseSunsetAndFlowerKeywords() {
        Set<SpotCategory> tagsSunrise = SpotCategoryTagger.tag("VE030400", "해맞이공원", "동해의 웅장한 해돋이와 낙조를 감상할 수 있습니다.");
        assertThat(tagsSunrise).contains(SpotCategory.SUNRISE_SUNSET);

        Set<SpotCategory> tagsFlower = SpotCategoryTagger.tag("VE030400", "수국테마공원", "여름철 화려한 수국과 가을 핑크뮬리가 장관입니다.");
        assertThat(tagsFlower).contains(SpotCategory.FLOWER);
    }

    @Test
    @DisplayName("소분류 코드 및 키워드가 없는 경우 ETC로 기본 귀속")
    void testFallbackToEtc() {
        Set<SpotCategory> tags = SpotCategoryTagger.tag(null, "평범한 장소", "아무런 특징이 없는 설명입니다.");

        assertThat(tags).containsExactly(SpotCategory.ETC);
    }
}