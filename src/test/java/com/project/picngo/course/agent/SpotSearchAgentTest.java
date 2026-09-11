package com.project.picngo.course.agent;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.course.agent.dto.PlanGoal;
import com.project.picngo.course.agent.dto.SpotCandidate;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SpotSearchAgentTest {

    @Mock
    private SpotRepository spotRepository;

    @InjectMocks
    private SpotSearchAgent spotSearchAgent;

    @Test
    @DisplayName("제주도나 강원도 같은 광역 명칭을 행정명칭과 일치하도록 정규화한다")
    void normalizeRegion_normalizesProvinceNames() {
        assertThat(SpotSearchAgent.normalizeRegion("제주도")).isEqualTo("제주");
        assertThat(SpotSearchAgent.normalizeRegion("강원도")).isEqualTo("강원");
        assertThat(SpotSearchAgent.normalizeRegion("경기도")).isEqualTo("경기");
        assertThat(SpotSearchAgent.normalizeRegion("부산광역시")).isEqualTo("부산");
        assertThat(SpotSearchAgent.normalizeRegion("서울특별시")).isEqualTo("서울");
        assertThat(SpotSearchAgent.normalizeRegion("전국")).isEqualTo("서울");
        assertThat(SpotSearchAgent.normalizeRegion("미정")).isEqualTo("서울");
        assertThat(SpotSearchAgent.normalizeRegion("")).isEqualTo("서울");
        assertThat(SpotSearchAgent.normalizeRegion(null)).isEqualTo("서울");
    }

    @Test
    @DisplayName("해당 지역 및 카테고리 스팟이 0건이면 전국 인기 스팟으로 최종 폴백한다")
    void searchCandidates_fallsBackToNationwideSpots_whenRegionEmpty() {
        // given
        PlanGoal goal = new PlanGoal("제주도", LocalDate.now(), 2, List.of(SpotCategory.BEACH), "해변", List.of());

        // 1차(카테고리), 2차(지역) 모두 0건
        given(spotRepository.findByRegionAndCategories(eq("제주"), anyCollection(), eq(SpotStatus.APPROVED), any(Pageable.class)))
                .willReturn(List.of());
        given(spotRepository.findByRegion(eq("제주"), eq(SpotStatus.APPROVED), any(Pageable.class)))
                .willReturn(List.of());

        // 3차: 전국 인기 스팟 반환
        Spot fallbackSpot = Spot.builder().build();
        ReflectionTestUtils.setField(fallbackSpot, "id", 999L);
        ReflectionTestUtils.setField(fallbackSpot, "name", "전국 명소 스팟");
        ReflectionTestUtils.setField(fallbackSpot, "address", "서울 종로구");
        ReflectionTestUtils.setField(fallbackSpot, "latitude", 37.5);
        ReflectionTestUtils.setField(fallbackSpot, "longitude", 126.9);
        ReflectionTestUtils.setField(fallbackSpot, "categories", Set.of(SpotCategory.PARK));
        ReflectionTestUtils.setField(fallbackSpot, "overview", "전국 명소 개요");

        given(spotRepository.findListByStatusAndIsActiveTrue(eq(SpotStatus.APPROVED), any(Pageable.class)))
                .willReturn(List.of(fallbackSpot));

        // when
        List<SpotCandidate> result = spotSearchAgent.searchCandidates(goal);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("전국 명소 스팟");
    }

    @Test
    @DisplayName("자연어 프롬프트에서 지역명을 안전하게 추출한다")
    void extractRegionFromPrompt_test() {
        assertThat(PlanGoal.extractRegionFromPrompt("1박 2일로 가고싶어", null)).isEqualTo("서울");
        assertThat(PlanGoal.extractRegionFromPrompt("제주도 1박2일 감성 출사", null)).isEqualTo("제주");
        assertThat(PlanGoal.extractRegionFromPrompt("부산에서 2박3일 여행", null)).isEqualTo("부산");
        assertThat(PlanGoal.extractRegionFromPrompt("강릉 바다 보고싶어", null)).isEqualTo("강릉");
        assertThat(PlanGoal.extractRegionFromPrompt("경주 벚꽃 출사", null)).isEqualTo("경주");
    }
}
