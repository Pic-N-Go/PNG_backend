package com.project.picngo.course.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.course.agent.WeatherAgent.WeatherBrief;
import com.project.picngo.course.agent.dto.CuratedCourseDraft;
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

@ExtendWith(MockitoExtension.class)
class CourseCuratorAgentTest {

    @Mock
    private OpenAiChatClient openAiChatClient;

    @InjectMocks
    private CourseCuratorAgent curatorAgent;

    @Test
    @DisplayName("OpenAI가 미설정된 환경에서도 룰베이스 폴백으로 안전하게 코스와 촬영 팁을 조립한다")
    void curateCourse_fallbackWhenOpenAiNotConfigured() {
        // given
        PlanGoal goal = new PlanGoal("부산", LocalDate.now(), List.of(SpotCategory.BEACH), "해변 감성 출사", List.of());
        SpotCandidate s1 = new SpotCandidate(1L, "해운대 해수욕장", "부산 해운대구", 35.158, 129.159, SpotCategory.BEACH, "해변");
        SpotCandidate s2 = new SpotCandidate(2L, "동백섬", "부산 해운대구", 35.153, 129.152, SpotCategory.PARK, "섬 산책로");
        WeatherBrief weather = new WeatherBrief("맑음", "18:00 ~ 19:00", "18:30");

        given(openAiChatClient.isConfigured()).willReturn(false);

        // when
        CuratedCourseDraft draft = curatorAgent.curateCourse(goal, List.of(s1, s2), List.of(0, 15), weather);

        // then
        assertThat(draft).isNotNull();
        assertThat(draft.title()).contains("부산");
        assertThat(draft.spots()).hasSize(2);
        assertThat(draft.spots().get(0).photographyTip()).contains("해운대 해수욕장");
        assertThat(draft.spots().get(1).photographyTip()).contains("일몰");
    }

    @Test
    @DisplayName("LLM 응답이 올 경우 가드레일이 작동하여 실존하는 스팟 순서대로 매핑된다")
    void curateCourse_withLlmOutputAndGuardrail() {
        // given
        PlanGoal goal = new PlanGoal("서울", LocalDate.now(), List.of(SpotCategory.NIGHT_VIEW), "도심 야경 출사", List.of());
        SpotCandidate s1 = new SpotCandidate(100L, "응봉산 팔각정", "서울 성동구", 37.550, 127.032, SpotCategory.NIGHT_VIEW, "야경 명소");
        WeatherBrief weather = new WeatherBrief("맑음", "18:30 ~ 19:30", "19:00");

        String llmJson = """
                {
                  "title": "서울 한강 조망 야경 출사 코스",
                  "overview": "황금빛 노을부터 한강 다리의 불빛까지 담을 수 있습니다.",
                  "tips": [
                    {
                      "spotId": 100,
                      "tip": "삼각대를 거치하고 조리개 F8로 한강 철교 궤적을 장노출로 담아보세요."
                    }
                  ]
                }
                """;

        given(openAiChatClient.isConfigured()).willReturn(true);
        given(openAiChatClient.chatJson(anyString(), anyString(), anyInt())).willReturn(Optional.of(llmJson));

        // when
        CuratedCourseDraft draft = curatorAgent.curateCourse(goal, List.of(s1), List.of(0), weather);

        // then
        assertThat(draft.title()).isEqualTo("서울 한강 조망 야경 출사 코스");
        assertThat(draft.spots()).hasSize(1);
        assertThat(draft.spots().get(0).photographyTip()).contains("삼각대를 거치하고");
    }

    @Test
    @DisplayName("2일 코스 큐레이션 시 일자별(DAY 1, DAY 2)로 dayNumber와 sequenceOrder가 올바르게 분배된다")
    void curateCourse_multiDay_assignsDayNumberAndResetsSequenceOrder() {
        // given: 2일 코스에 4개 스팟 제공
        PlanGoal goal = new PlanGoal("제주", LocalDate.now(), 2, List.of(SpotCategory.BEACH), "제주 해변 출사", List.of());
        SpotCandidate s1 = new SpotCandidate(1L, "함덕해변", "제주", 33.5, 126.6, SpotCategory.BEACH, "");
        SpotCandidate s2 = new SpotCandidate(2L, "김녕해수욕장", "제주", 33.55, 126.7, SpotCategory.BEACH, "");
        SpotCandidate s3 = new SpotCandidate(3L, "협재해변", "제주", 33.39, 126.2, SpotCategory.BEACH, "");
        SpotCandidate s4 = new SpotCandidate(4L, "금능해변", "제주", 33.38, 126.23, SpotCategory.BEACH, "");

        WeatherBrief weather = new WeatherBrief("맑음", "18:00 ~ 19:00", "18:30");
        given(openAiChatClient.isConfigured()).willReturn(false); // 룰베이스 폴백 검증

        // when
        CuratedCourseDraft draft = curatorAgent.curateCourse(
                goal,
                List.of(s1, s2, s3, s4),
                List.of(0, 20, 45, 10),
                weather
        );

        // then
        assertThat(draft.durationDays()).isEqualTo(2);
        assertThat(draft.spots()).hasSize(4);

        // DAY 1 검증
        assertThat(draft.spots().get(0).dayNumber()).isEqualTo(1);
        assertThat(draft.spots().get(0).sequenceOrder()).isEqualTo(1);
        assertThat(draft.spots().get(0).estimatedTravelMinutes()).isEqualTo(0);

        assertThat(draft.spots().get(1).dayNumber()).isEqualTo(1);
        assertThat(draft.spots().get(1).sequenceOrder()).isEqualTo(2);
        assertThat(draft.spots().get(1).estimatedTravelMinutes()).isEqualTo(20);

        // DAY 2 검증 (dayNumber 2, sequenceOrder 1부터 리셋, 새 날 시작 이동시간 0분)
        assertThat(draft.spots().get(2).dayNumber()).isEqualTo(2);
        assertThat(draft.spots().get(2).sequenceOrder()).isEqualTo(1);
        assertThat(draft.spots().get(2).estimatedTravelMinutes()).isEqualTo(0);

        assertThat(draft.spots().get(3).dayNumber()).isEqualTo(2);
        assertThat(draft.spots().get(3).sequenceOrder()).isEqualTo(2);
        assertThat(draft.spots().get(3).estimatedTravelMinutes()).isEqualTo(10);
    }
}
