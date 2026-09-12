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
    @DisplayName("제주도나 충남 같은 광역 명칭을 행정명칭과 일치하도록 정규화한다")
    void normalizeRegion_normalizesProvinceNames() {
        assertThat(SpotSearchAgent.resolveRegionPair("충남")).isEqualTo(new SpotSearchAgent.RegionPair("충청남도", "충남"));
        assertThat(SpotSearchAgent.resolveRegionPair("충남 서해")).isEqualTo(new SpotSearchAgent.RegionPair("충청남도", "충남"));
        assertThat(SpotSearchAgent.resolveRegionPair("제주도")).isEqualTo(new SpotSearchAgent.RegionPair("제주", "제주특별자치도"));
        assertThat(SpotSearchAgent.resolveRegionPair("강원도")).isEqualTo(new SpotSearchAgent.RegionPair("강원", "강원특별자치도"));
        assertThat(SpotSearchAgent.resolveRegionPair("부산광역시")).isEqualTo(new SpotSearchAgent.RegionPair("부산", "부산광역시"));
        assertThat(SpotSearchAgent.resolveRegionPair("전국")).isEqualTo(new SpotSearchAgent.RegionPair("서울", "서울특별시"));
    }

    @Test
    @DisplayName("해당 지역 및 카테고리 스팟이 0건이면 전국 인기 스팟으로 최종 폴백하고 이상 좌표(대만 해역 등)는 필터링한다")
    void searchCandidates_fallsBackToNationwideSpots_whenRegionEmpty() {
        // given
        PlanGoal goal = new PlanGoal("제주도", LocalDate.now(), 2, List.of(SpotCategory.BEACH), "해변", List.of());

        // 1차(카테고리), 2차(지역) 모두 0건
        given(spotRepository.findByRegionAndCategories(anyString(), any(), anyCollection(), eq(SpotStatus.APPROVED), any(Pageable.class)))
                .willReturn(List.of());
        given(spotRepository.findByRegion(anyString(), any(), eq(SpotStatus.APPROVED), any(Pageable.class)))
                .willReturn(List.of());

        // 3차: 전국 스팟 중 정상 스팟 1개 + 대만 남중국해 이상 좌표(19.69, 117.99) 스팟 1개 반환
        Spot validSpot = Spot.builder().build();
        ReflectionTestUtils.setField(validSpot, "id", 100L);
        ReflectionTestUtils.setField(validSpot, "name", "정상 스팟");
        ReflectionTestUtils.setField(validSpot, "address", "충남 태안군");
        ReflectionTestUtils.setField(validSpot, "latitude", 36.7);
        ReflectionTestUtils.setField(validSpot, "longitude", 126.1);
        ReflectionTestUtils.setField(validSpot, "categories", Set.of(SpotCategory.BEACH));
        ReflectionTestUtils.setField(validSpot, "overview", "서해안 해변");

        Spot abnormalSpot = Spot.builder().build();
        ReflectionTestUtils.setField(abnormalSpot, "id", 200L);
        ReflectionTestUtils.setField(abnormalSpot, "name", "대만 해역 이상 좌표 스팟");
        ReflectionTestUtils.setField(abnormalSpot, "address", "이상 좌표");
        ReflectionTestUtils.setField(abnormalSpot, "latitude", 19.694427);
        ReflectionTestUtils.setField(abnormalSpot, "longitude", 117.992566);
        ReflectionTestUtils.setField(abnormalSpot, "categories", Set.of(SpotCategory.PARK));
        ReflectionTestUtils.setField(abnormalSpot, "overview", "이상 좌표");

        given(spotRepository.findListByStatusAndIsActiveTrue(eq(SpotStatus.APPROVED), any(Pageable.class)))
                .willReturn(List.of(validSpot, abnormalSpot));

        // when
        List<SpotCandidate> result = spotSearchAgent.searchCandidates(goal);

        // then: 대만 해역 이상 좌표 스팟은 제외되고 정상 스팟만 반환되어야 함
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(100L);
        assertThat(result.get(0).name()).isEqualTo("정상 스팟");
    }

    @Test
    @DisplayName("자연어 프롬프트에서 지역명을 안전하게 추출한다")
    void extractRegionFromPrompt_test() {
        assertThat(PlanGoal.extractRegionFromPrompt("1박 2일로 가고싶어", null)).isEqualTo("서울");
        assertThat(PlanGoal.extractRegionFromPrompt("충남 서해 바다를 보러 1박2일로 놀러갈거야", null)).isEqualTo("충남");
        assertThat(PlanGoal.extractRegionFromPrompt("제주도 1박2일 감성 출사", null)).isEqualTo("제주");
        assertThat(PlanGoal.extractRegionFromPrompt("부산에서 2박3일 여행", null)).isEqualTo("부산");
        assertThat(PlanGoal.extractRegionFromPrompt("태안 꽃지해수욕장 가고싶어", null)).isEqualTo("태안");
    }
}
