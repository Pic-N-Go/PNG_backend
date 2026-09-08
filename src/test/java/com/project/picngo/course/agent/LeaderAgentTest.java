package com.project.picngo.course.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.course.agent.RouteOptimizerAgent.OptimizedRoute;
import com.project.picngo.course.agent.WeatherAgent.WeatherBrief;
import com.project.picngo.course.agent.dto.CuratedCourseDraft;
import com.project.picngo.course.agent.dto.CuratedSpotItem;
import com.project.picngo.course.agent.dto.PlanGoal;
import com.project.picngo.course.agent.dto.SpotCandidate;
import com.project.picngo.external.OpenAiChatClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LeaderAgentTest {

    @Mock
    private OpenAiChatClient openAiChatClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SpotSearchAgent spotSearchAgent;

    @Mock
    private RouteOptimizerAgent routeOptimizerAgent;

    @Mock
    private WeatherAgent weatherAgent;

    @Mock
    private CourseCuratorAgent courseCuratorAgent;

    @InjectMocks
    private LeaderAgent leaderAgent;

    @Test
    @DisplayName("자연어 프롬프트가 주어지면 의도를 분석하고 전문 워커 에이전트들을 조율하여 최종 코스를 기획한다")
    void planCourse_orchestratesAllSpecializedAgents() {
        // given
        String prompt = "이번 주말 부산에서 노을과 야경 보기 좋은 출사 코스 추천해줘";
        String region = "부산";
        LocalDate date = LocalDate.of(2026, 9, 12);

        // 1. LLM 의도 분석 모킹
        String jsonIntent = """
                {
                  "region": "부산",
                  "theme": "황혼과 야경 출사",
                  "categories": ["SUNRISE_SUNSET", "NIGHT_VIEW"]
                }
                """;
        given(openAiChatClient.isConfigured()).willReturn(true);
        given(openAiChatClient.chatJson(anyString(), anyString(), anyInt()))
                .willReturn(Optional.of(jsonIntent));

        // 2. SpotSearchAgent 후보 스팟 모킹
        SpotCandidate s1 = new SpotCandidate(10L, "다대포 해수욕장", "부산 사하구", 35.047, 128.966, SpotCategory.SUNRISE_SUNSET, "일몰 명소");
        SpotCandidate s2 = new SpotCandidate(20L, "황령산 봉수대", "부산 부산진구", 35.158, 129.082, SpotCategory.NIGHT_VIEW, "야경 명소");
        List<SpotCandidate> candidates = List.of(s1, s2);
        given(spotSearchAgent.searchCandidates(any(PlanGoal.class))).willReturn(candidates);

        // 3. RouteOptimizerAgent 동선 최적화 모킹
        OptimizedRoute optimizedRoute = new OptimizedRoute(List.of(s1, s2), List.of(0, 35));
        given(routeOptimizerAgent.optimizeRoute(eq(candidates), eq(4))).willReturn(optimizedRoute);

        // 4. WeatherAgent 날씨 분석 모킹
        WeatherBrief weather = new WeatherBrief("맑음", "18:15 ~ 19:15", "18:45");
        given(weatherAgent.analyzeWeather(eq(35.047), eq(128.966), eq(date))).willReturn(weather);

        // 5. CourseCuratorAgent 큐레이션 모킹
        CuratedSpotItem item1 = new CuratedSpotItem(10L, "다대포 해수욕장", 1, "골든아워 갯벌 실루엣 촬영", 0);
        CuratedSpotItem item2 = new CuratedSpotItem(20L, "황령산 봉수대", 2, "부산 도심 불빛 장노출 촬영", 35);
        CuratedCourseDraft draft = new CuratedCourseDraft(
                "부산 황혼과 야경 출사 코스",
                "황혼과 야경 출사",
                "바다 일몰과 도심 야경을 한 번에 담는 코스",
                List.of(item1, item2)
        );
        given(courseCuratorAgent.curateCourse(any(), eq(List.of(s1, s2)), eq(List.of(0, 35)), eq(weather)))
                .willReturn(draft);

        // when
        CuratedCourseDraft result = leaderAgent.planCourse(prompt, region, date);

        // then
        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("부산 황혼과 야경 출사 코스");
        assertThat(result.spots()).hasSize(2);
        assertThat(result.spots().get(0).spotName()).isEqualTo("다대포 해수욕장");
        assertThat(result.spots().get(1).spotName()).isEqualTo("황령산 봉수대");

        verify(spotSearchAgent).searchCandidates(any(PlanGoal.class));
        verify(routeOptimizerAgent).optimizeRoute(candidates, 4);
        verify(weatherAgent).analyzeWeather(35.047, 128.966, date);
        verify(courseCuratorAgent).curateCourse(any(), anyList(), anyList(), eq(weather));
    }
}
