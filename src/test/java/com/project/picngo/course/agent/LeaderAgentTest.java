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

    @Test
    @DisplayName("1박2일 요청 시 durationDays=2를 추출하고 일수에 비례하여 스팟을 요청한다")
    void planCourse_multiDayRequest_extractsDurationDaysAndScalesSpots() {
        // given
        String prompt = "부산 1박 2일 감성 바다 출사 코스 추천해줘";
        String region = "부산";
        LocalDate date = LocalDate.of(2026, 9, 12);

        String jsonIntent = """
                {
                  "region": "부산",
                  "theme": "1박2일 바다 감성 출사",
                  "durationDays": 2,
                  "categories": ["BEACH", "SUNRISE_SUNSET"]
                }
                """;
        given(openAiChatClient.isConfigured()).willReturn(true);
        given(openAiChatClient.chatJson(anyString(), anyString(), anyInt()))
                .willReturn(Optional.of(jsonIntent));

        SpotCandidate s1 = new SpotCandidate(1L, "해운대", "부산", 35.1, 129.1, SpotCategory.BEACH, "");
        SpotCandidate s2 = new SpotCandidate(2L, "광안리", "부산", 35.15, 129.11, SpotCategory.BEACH, "");
        List<SpotCandidate> candidates = List.of(s1, s2);
        given(spotSearchAgent.searchCandidates(any(PlanGoal.class))).willReturn(candidates);

        OptimizedRoute optimizedRoute = new OptimizedRoute(List.of(s1, s2), List.of(0, 20));
        // durationDays(2) * spotsPerDay(3) = 6개 스팟 요청
        given(routeOptimizerAgent.optimizeRoute(eq(candidates), eq(6))).willReturn(optimizedRoute);

        WeatherBrief weather = new WeatherBrief("맑음", "18:00 ~ 19:00", "18:30");
        given(weatherAgent.analyzeWeather(anyDouble(), anyDouble(), eq(date))).willReturn(weather);

        CuratedSpotItem item1 = new CuratedSpotItem(1L, "해운대", 1, 1, "DAY 1 팁", 0);
        CuratedSpotItem item2 = new CuratedSpotItem(2L, "광안리", 2, 1, "DAY 2 팁", 0);
        CuratedCourseDraft draft = new CuratedCourseDraft("부산 1박2일 코스", "바다 감성", "개요", 2, List.of(item1, item2));
        given(courseCuratorAgent.curateCourse(any(), eq(List.of(s1, s2)), eq(List.of(0, 20)), eq(weather)))
                .willReturn(draft);

        // when
        CuratedCourseDraft result = leaderAgent.planCourse(prompt, region, date);

        // then
        assertThat(result.durationDays()).isEqualTo(2);
        assertThat(result.spots().get(0).dayNumber()).isEqualTo(1);
        assertThat(result.spots().get(1).dayNumber()).isEqualTo(2);
        verify(routeOptimizerAgent).optimizeRoute(candidates, 6);
    }

    @Test
    @DisplayName("자연어 프롬프트에서 여행 일수를 정규식과 규칙으로 정확히 파싱한다")
    void parseDurationDays_test() {
        assertThat(PlanGoal.parseDurationDays("1박 2일 부산 여행")).isEqualTo(2);
        assertThat(PlanGoal.parseDurationDays("2박3일 제주도 출사 코스")).isEqualTo(3);
        assertThat(PlanGoal.parseDurationDays("3박 4일 강원도")).isEqualTo(4);
        assertThat(PlanGoal.parseDurationDays("당일치기 서울 출사")).isEqualTo(1);
        assertThat(PlanGoal.parseDurationDays("이틀 동안 경주 코스")).isEqualTo(2);
        assertThat(PlanGoal.parseDurationDays("3일 코스 추천해줘")).isEqualTo(3);
        assertThat(PlanGoal.parseDurationDays("사흘간 부산 여행")).isEqualTo(3);
        assertThat(PlanGoal.parseDurationDays("")).isEqualTo(1);
        assertThat(PlanGoal.parseDurationDays(null)).isEqualTo(1);
    }
}
