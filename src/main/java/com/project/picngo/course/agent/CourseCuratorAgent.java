package com.project.picngo.course.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.picngo.course.agent.WeatherAgent.WeatherBrief;
import com.project.picngo.course.agent.dto.CuratedCourseDraft;
import com.project.picngo.course.agent.dto.CuratedSpotItem;
import com.project.picngo.course.agent.dto.PlanGoal;
import com.project.picngo.course.agent.dto.SpotCandidate;
import com.project.picngo.external.OpenAiChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * [워커 4: 코스 큐레이터 전문 에이전트]
 * 날씨, 골든아워, 정렬된 스팟 데이터를 바탕으로 시간대별 촬영 팁과 감성 코스 가이드를 조립합니다.
 * 출력 결과에 대한 화이트리스트 검증(Guardrail)을 적용하여 신뢰성을 보장합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseCuratorAgent {

    private final OpenAiChatClient openAiChatClient;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public CuratedCourseDraft curateCourse(
            PlanGoal goal,
            List<SpotCandidate> orderedSpots,
            List<Integer> travelMinutesList,
            WeatherBrief weather
    ) {
        int duration = Math.max(1, goal.durationDays());
        if (orderedSpots == null || orderedSpots.isEmpty()) {
            return new CuratedCourseDraft("추천 출사 코스", goal.theme(), "추천 가능한 스팟이 부족합니다.", duration, List.of());
        }

        // LLM이 설정되어 있으면 촬영 팁 및 타이틀 생성 시도
        if (openAiChatClient.isConfigured()) {
            try {
                Optional<CuratedCourseDraft> result = generateWithLlm(goal, orderedSpots, travelMinutesList, weather);
                if (result.isPresent()) {
                    return result.get();
                }
            } catch (Exception e) {
                log.warn("CourseCuratorAgent LLM 큐레이션 실패, 룰베이스 폴백 적용: {}", e.getMessage());
            }
        }

        return fallbackCurate(goal, orderedSpots, travelMinutesList, weather);
    }

    private Optional<CuratedCourseDraft> generateWithLlm(
            PlanGoal goal,
            List<SpotCandidate> spots,
            List<Integer> travelTimes,
            WeatherBrief weather
    ) throws Exception {
        String systemPrompt = """
                당신은 사진 촬영 전문 여행 큐레이터입니다.
                제공된 출사 스팟 목록, 이동 순서, 날씨, 골든아워 정보를 바탕으로 멋진 코스 제목과 스팟별 맞춤형 사진 촬영 팁(memo)을 작성해야 합니다.
                
                반드시 아래 JSON 형식으로만 응답해야 합니다:
                {
                  "title": "코스 제목 (30자 이내)",
                  "overview": "코스 소개 및 총평 (100자 내외)",
                  "tips": [
                    {
                      "spotId": 123,
                      "tip": "해당 스팟에서의 구체적인 촬영 팁 및 구도 추천 (40~70자)"
                    }
                  ]
                }
                """;

        int duration = Math.max(1, goal.durationDays());
        int totalSpots = spots.size();

        StringBuilder userContext = new StringBuilder();
        userContext.append("지역: ").append(goal.region()).append("\n");
        userContext.append("테마: ").append(goal.theme()).append("\n");
        userContext.append(String.format("여행 일정: %d일 코스 (총 %d곳)\n", duration, totalSpots));
        userContext.append("날씨: ").append(weather.weatherSummary())
                .append(", 일몰: ").append(weather.sunsetTime())
                .append(", 골든아워: ").append(weather.goldenHourTime()).append("\n\n");
        userContext.append("일정별 스팟 목록:\n");

        int curDay = 1;
        int orderInCurDay = 1;
        for (int i = 0; i < totalSpots; i++) {
            SpotCandidate s = spots.get(i);
            int day = (totalSpots > 0) ? Math.min(duration, (i * duration / totalSpots) + 1) : 1;
            if (day != curDay) {
                curDay = day;
                orderInCurDay = 1;
            }
            int travelMin = (i < travelTimes.size()) ? travelTimes.get(i) : 0;
            if (orderInCurDay == 1) {
                travelMin = 0;
            }
            userContext.append(String.format("[DAY %d - %d번째] ID: %d, %s (%s) - 이동: %d분 소요\n",
                    day, orderInCurDay++, s.id(), s.name(), s.address(), travelMin));
        }

        Optional<String> jsonOpt = openAiChatClient.chatJson(systemPrompt, userContext.toString(), 1000);
        if (jsonOpt.isEmpty()) {
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(jsonOpt.get());
        String title = root.path("title").asText("감성 출사 코스");
        String overview = root.path("overview").asText("멋진 사진을 남길 수 있는 추천 출사 코스입니다.");

        Map<Long, String> tipsMap = new HashMap<>();
        JsonNode tipsNode = root.path("tips");
        if (tipsNode.isArray()) {
            for (JsonNode item : tipsNode) {
                long spotId = item.path("spotId").asLong();
                String tip = item.path("tip").asText();
                tipsMap.put(spotId, tip);
            }
        }

        // 출력 가드레일: 입력으로 주어진 스팟 순서 그대로 조립하고, 매핑되지 않은 스팟은 기본 팁 제공
        List<CuratedSpotItem> curatedSpots = new ArrayList<>();
        int currentDay = 1;
        int orderInDay = 1;
        for (int i = 0; i < spots.size(); i++) {
            SpotCandidate spot = spots.get(i);
            int day = (totalSpots > 0) ? Math.min(duration, (i * duration / totalSpots) + 1) : 1;
            if (day != currentDay) {
                currentDay = day;
                orderInDay = 1;
            }
            int travelMin = (i < travelTimes.size()) ? travelTimes.get(i) : 0;
            if (orderInDay == 1) {
                travelMin = 0;
            }
            String tip = tipsMap.getOrDefault(spot.id(),
                    String.format("%s의 매력적인 풍경과 색감을 담아보세요. (골든아워 %s 활용 추천)", spot.name(), weather.goldenHourTime()));

            curatedSpots.add(new CuratedSpotItem(
                    spot.id(),
                    spot.name(),
                    day,
                    orderInDay++,
                    tip,
                    travelMin
            ));
        }

        return Optional.of(new CuratedCourseDraft(title, goal.theme(), overview, duration, curatedSpots));
    }

    private CuratedCourseDraft fallbackCurate(
            PlanGoal goal,
            List<SpotCandidate> spots,
            List<Integer> travelTimes,
            WeatherBrief weather
    ) {
        int duration = Math.max(1, goal.durationDays());
        int totalSpots = spots.size();
        String title = String.format("%s %s (%d일 코스)", goal.region(), goal.theme(), duration);
        String overview = String.format("%s 날씨에 어울리는 %s %d일 추천 코스입니다. (골든아워: %s)",
                weather.weatherSummary(), goal.region(), duration, weather.goldenHourTime());

        List<CuratedSpotItem> curatedSpots = new ArrayList<>();
        int currentDay = 1;
        int orderInDay = 1;
        for (int i = 0; i < spots.size(); i++) {
            SpotCandidate spot = spots.get(i);
            int day = (totalSpots > 0) ? Math.min(duration, (i * duration / totalSpots) + 1) : 1;
            if (day != currentDay) {
                currentDay = day;
                orderInDay = 1;
            }
            int travelMin = (i < travelTimes.size()) ? travelTimes.get(i) : 0;
            if (orderInDay == 1) {
                travelMin = 0;
            }
            String tip = (orderInDay == 1 && i > 0)
                    ? String.format("%d일차를 여는 상쾌한 분위기의 %s 풍경을 담아보세요.", day, spot.name())
                    : (i == spots.size() - 1)
                    ? String.format("일몰(%s) 및 야경 시간대에 맞춰 아름다운 황혼을 담아보세요.", weather.sunsetTime())
                    : String.format("%s의 시원한 풍경을 다양한 화각으로 담아보세요.", spot.name());

            curatedSpots.add(new CuratedSpotItem(
                    spot.id(),
                    spot.name(),
                    day,
                    orderInDay++,
                    tip,
                    travelMin
            ));
        }

        return new CuratedCourseDraft(title, goal.theme(), overview, duration, curatedSpots);
    }
}
