package com.project.picngo.course.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.course.agent.RouteOptimizerAgent.OptimizedRoute;
import com.project.picngo.course.agent.WeatherAgent.WeatherBrief;
import com.project.picngo.course.agent.dto.CuratedCourseDraft;
import com.project.picngo.course.agent.dto.PlanGoal;
import com.project.picngo.course.agent.dto.SpotCandidate;
import com.project.picngo.external.OpenAiChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * [지휘자: 리더 오케스트레이터 에이전트]
 * 사용자의 복합적인 자연어 출사 요청을 분석하고, 전문 서브 에이전트들을 조율하여
 * 최적화된 출사 코스를 기획하는 중앙 관제 에이전트입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaderAgent {

    private final OpenAiChatClient openAiChatClient;
    private final ObjectMapper objectMapper;
    private final SpotSearchAgent spotSearchAgent;
    private final RouteOptimizerAgent routeOptimizerAgent;
    private final WeatherAgent weatherAgent;
    private final CourseCuratorAgent courseCuratorAgent;

    public CuratedCourseDraft planCourse(String userPrompt, String requestedRegion, LocalDate targetDate) {
        log.info("══════════════════════════════════════════════════════");
        log.info("🎯 [LeaderAgent] 멀티 에이전트 오케스트레이션 파이프라인 시작");
        log.info(" - 사용자 프롬프트: {}", userPrompt);
        log.info(" - 요청 지역: {}, 타겟 날짜: {}", requestedRegion, targetDate);
        log.info("══════════════════════════════════════════════════════");

        // Step 1: 자연어 의도 분석 및 목표(PlanGoal) 구조화
        PlanGoal goal = analyzeIntent(userPrompt, requestedRegion, targetDate);
        log.info("📌 [Step 1 완료] 구조화된 목표: region={}, theme={}, categories={}",
                goal.region(), goal.theme(), goal.categories());

        // Step 2: 실존 스팟 후보지 탐색 (환각 방지)
        List<SpotCandidate> candidates = spotSearchAgent.searchCandidates(goal);
        log.info("📌 [Step 2 완료] 후보 스팟 발굴: {}건", candidates.size());

        if (candidates.isEmpty()) {
            log.warn("해당 지역에 등록된 스팟이 없어 기획을 중단합니다: region={}", goal.region());
            return new CuratedCourseDraft(goal.region() + " 출사 코스", goal.theme(), "해당 지역에 추천할 수 있는 스팟이 없습니다.", List.of());
        }

        // Step 3: 동선 최적화 (Greedy Nearest Neighbor 알고리즘)
        OptimizedRoute route = routeOptimizerAgent.optimizeRoute(candidates, 4);
        log.info("📌 [Step 3 완료] 동선 최적화 완료: 선정 스팟 {}곳", route.orderedSpots().size());

        // Step 4: 기상 상태 및 골든아워 분석
        SpotCandidate firstSpot = route.orderedSpots().get(0);
        WeatherBrief weather = weatherAgent.analyzeWeather(
                firstSpot.latitude(),
                firstSpot.longitude(),
                goal.targetDate()
        );
        log.info("📌 [Step 4 완료] 날씨 분석: 상태={}, 일몰={}, 골든아워={}",
                weather.weatherSummary(), weather.sunsetTime(), weather.goldenHourTime());

        // Step 5: 감성 큐레이션 및 시간대별 촬영 가이드 조립
        CuratedCourseDraft draft = courseCuratorAgent.curateCourse(
                goal,
                route.orderedSpots(),
                route.travelMinutesList(),
                weather
        );
        log.info("🎯 [LeaderAgent] 오케스트레이션 성공! 완성된 코스: '{}'", draft.title());

        return draft;
    }

    public PlanGoal analyzeIntent(String prompt, String region, LocalDate targetDate) {
        if (!openAiChatClient.isConfigured() || prompt == null || prompt.isBlank()) {
            return PlanGoal.fallback(prompt, region, targetDate);
        }

        try {
            String systemPrompt = """
                    당신은 출사 여행 플래너의 리더 에이전트입니다.
                    사용자의 요청에서 '지역(region)', '출사 테마(theme)', '관련 카테고리(categories)'를 추출하여 JSON으로 응답하세요.
                    
                    가능한 categories 목록 (최대 3개 선택):
                    ["BEACH", "PARK", "MOUNTAIN", "HANOK", "FOREST", "HERITAGE", "CAFE", "CITY", "NIGHT_VIEW", "FESTIVAL", "FLOWER", "SUNRISE_SUNSET", "MILKY_WAY"]
                    
                    응답 형식 예시:
                    {
                      "region": "부산",
                      "theme": "노을과 야경 출사",
                      "categories": ["SUNRISE_SUNSET", "NIGHT_VIEW", "BEACH"]
                    }
                    """;

            String userMessage = String.format("요청: \"%s\" (기본 지역: %s)", prompt, region != null ? region : "미지정");
            Optional<String> jsonOpt = openAiChatClient.chatJson(systemPrompt, userMessage, 500);

            if (jsonOpt.isPresent()) {
                JsonNode root = objectMapper.readTree(jsonOpt.get());
                String extractedRegion = root.path("region").asText(region != null ? region : "서울");
                String theme = root.path("theme").asText("감성 출사 코스");

                List<SpotCategory> categories = new ArrayList<>();
                JsonNode catNode = root.path("categories");
                if (catNode.isArray()) {
                    for (JsonNode c : catNode) {
                        try {
                            categories.add(SpotCategory.valueOf(c.asText().toUpperCase()));
                        } catch (Exception ignored) {}
                    }
                }
                if (categories.isEmpty()) {
                    categories.add(SpotCategory.SUNRISE_SUNSET);
                }

                LocalDate date = (targetDate != null) ? targetDate : LocalDate.now().plusDays(1);
                return new PlanGoal(extractedRegion, date, categories, theme, List.of());
            }

        } catch (Exception e) {
            log.warn("LeaderAgent 의도 분석 실패, 룰베이스 폴백 적용: {}", e.getMessage());
        }

        return PlanGoal.fallback(prompt, region, targetDate);
    }
}
