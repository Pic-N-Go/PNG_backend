package com.project.picngo.spotalert.service;

import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.spotalert.domain.enums.TimeCondition;
import com.project.picngo.spotalert.domain.enums.WeatherCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class WeatherMatchServiceTest {

    private final WeatherMatchService sut = new WeatherMatchService();

    private static final String DATE = "20260728";
    private static final String OTHER_DATE = "20260729";

    /** 테스트용 예보 슬롯 생성 헬퍼 */
    private static WeatherForecastResponse f(String date, String time, String status) {
        return new WeatherForecastResponse(date, time, status, 0.0);
    }

    @Nested
    @DisplayName("정밀 매칭 - 단기예보(시간별) 데이터가 있을 때 시간대 윈도우로 판정")
    class PreciseMatch {

        @Test
        @DisplayName("DAWN(04~06): 새벽 슬롯이 조건에 맞으면 true")
        void dawnMatchesWithinWindow() {
            List<WeatherForecastResponse> forecast = List.of(
                    f(DATE, "0500", "CLEAR"),
                    f(DATE, "1400", "RAINY")
            );
            assertThat(sut.matches(forecast, DATE, TimeCondition.DAWN, Set.of(WeatherCondition.CLEAR)))
                    .isTrue();
        }

        @Test
        @DisplayName("[회귀 방지] DAWN은 새벽 슬롯만 본다 - 새벽은 비이고 한낮만 맑아도 false")
        void dawnIgnoresMiddaySlots() {
            // 예전 버그: 시간대 무관하게 10/14/18시만 봐서 새벽 촬영인데 한낮 날씨로 오판정했음
            List<WeatherForecastResponse> forecast = List.of(
                    f(DATE, "0500", "RAINY"),  // 실제 새벽 = 비
                    f(DATE, "1000", "CLEAR"),  // 한낮 = 맑음 (예전엔 이걸 보고 매칭됐음)
                    f(DATE, "1400", "CLEAR")
            );
            assertThat(sut.matches(forecast, DATE, TimeCondition.DAWN, Set.of(WeatherCondition.CLEAR)))
                    .isFalse();
        }

        @Test
        @DisplayName("NIGHT(19~23): 야간 슬롯으로 판정한다")
        void nightMatchesWithinWindow() {
            List<WeatherForecastResponse> forecast = List.of(
                    f(DATE, "1000", "RAINY"),
                    f(DATE, "2100", "CLEAR")
            );
            assertThat(sut.matches(forecast, DATE, TimeCondition.NIGHT, Set.of(WeatherCondition.CLEAR)))
                    .isTrue();
        }

        @Test
        @DisplayName("MORNING(07~11): 오전 슬롯으로 판정한다")
        void morningMatchesWithinWindow() {
            List<WeatherForecastResponse> forecast = List.of(
                    f(DATE, "0900", "CLOUDY"),
                    f(DATE, "1800", "CLEAR")
            );
            assertThat(sut.matches(forecast, DATE, TimeCondition.MORNING, Set.of(WeatherCondition.CLOUDY)))
                    .isTrue();
        }

        @Test
        @DisplayName("AFTERNOON(12~16): 오후 슬롯으로 판정한다")
        void afternoonMatchesWithinWindow() {
            List<WeatherForecastResponse> forecast = List.of(
                    f(DATE, "0500", "CLEAR"),
                    f(DATE, "1400", "SNOWY")
            );
            assertThat(sut.matches(forecast, DATE, TimeCondition.AFTERNOON, Set.of(WeatherCondition.SNOWY)))
                    .isTrue();
        }

        @Test
        @DisplayName("윈도우 안에 시간별 슬롯이 있으면 AM/PM 폴백을 타지 않는다")
        void preciseTakesPrecedenceOverFallback() {
            // 04~06 안에 0600 슬롯이 있으므로 폴백(1000)을 보지 않아야 한다
            List<WeatherForecastResponse> forecast = List.of(
                    f(DATE, "0600", "RAINY"),  // 새벽 실제 = 비
                    f(DATE, "1000", "CLEAR")   // 폴백 슬롯이지만 무시돼야 함
            );
            assertThat(sut.matches(forecast, DATE, TimeCondition.DAWN, Set.of(WeatherCondition.CLEAR)))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("근사 매칭 - 중기예보(AM/PM만) 데이터일 때 반나절 슬롯으로 폴백")
    class AmPmFallback {

        // 중기예보는 오전=1000, 오후=1400·1800으로만 매핑되어 온다 (시간별 슬롯 없음)

        @Test
        @DisplayName("DAWN은 오전(1000)으로 폴백 매칭된다")
        void dawnFallsBackToAm() {
            List<WeatherForecastResponse> midTermOnly = List.of(
                    f(DATE, "1000", "CLEAR"),   // 오전
                    f(DATE, "1400", "RAINY"),   // 오후
                    f(DATE, "1800", "RAINY")
            );
            assertThat(sut.matches(midTermOnly, DATE, TimeCondition.DAWN, Set.of(WeatherCondition.CLEAR)))
                    .isTrue();
        }

        @Test
        @DisplayName("NIGHT은 오후(1400/1800)로 폴백 매칭된다")
        void nightFallsBackToPm() {
            List<WeatherForecastResponse> midTermOnly = List.of(
                    f(DATE, "1000", "CLEAR"),   // 오전
                    f(DATE, "1400", "RAINY"),   // 오후
                    f(DATE, "1800", "RAINY")
            );
            assertThat(sut.matches(midTermOnly, DATE, TimeCondition.NIGHT, Set.of(WeatherCondition.RAINY)))
                    .isTrue();
        }

        @Test
        @DisplayName("NIGHT은 오전 슬롯을 보지 않는다 - 오전만 조건에 맞으면 false")
        void nightDoesNotUseAmSlot() {
            List<WeatherForecastResponse> midTermOnly = List.of(
                    f(DATE, "1000", "CLEAR"),   // 오전만 맑음
                    f(DATE, "1400", "RAINY"),
                    f(DATE, "1800", "RAINY")
            );
            // NIGHT은 오후(PM)에 매핑되므로 오전의 CLEAR로는 매칭되면 안 된다
            assertThat(sut.matches(midTermOnly, DATE, TimeCondition.NIGHT, Set.of(WeatherCondition.CLEAR)))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("matches - 공통 규칙")
    class CommonRules {

        @Test
        @DisplayName("NONE 조건이 포함되면 예보와 무관하게 항상 true")
        void noneConditionAlwaysMatches() {
            List<WeatherForecastResponse> forecast = List.of(f(DATE, "0500", "RAINY"));
            assertThat(sut.matches(forecast, DATE, TimeCondition.DAWN, Set.of(WeatherCondition.NONE)))
                    .isTrue();
        }

        @Test
        @DisplayName("원하는 날씨 조건이 비었거나 null이면 false")
        void emptyOrNullConditionsReturnFalse() {
            List<WeatherForecastResponse> forecast = List.of(f(DATE, "0500", "CLEAR"));
            assertThat(sut.matches(forecast, DATE, TimeCondition.DAWN, Set.of())).isFalse();
            assertThat(sut.matches(forecast, DATE, TimeCondition.DAWN, null)).isFalse();
        }

        @Test
        @DisplayName("targetDate와 다른 날짜의 슬롯은 무시한다")
        void ignoresOtherDates() {
            List<WeatherForecastResponse> forecast = List.of(
                    f(OTHER_DATE, "0500", "CLEAR")  // 다른 날의 새벽 맑음
            );
            assertThat(sut.matches(forecast, DATE, TimeCondition.DAWN, Set.of(WeatherCondition.CLEAR)))
                    .isFalse();
        }

        @Test
        @DisplayName("알 수 없는 날씨 상태는 예외 없이 스킵된다")
        void unknownStatusIsSkippedWithoutError() {
            List<WeatherForecastResponse> forecast = List.of(f(DATE, "0500", "UNKNOWN_XYZ"));
            assertThatCode(() ->
                    assertThat(sut.matches(forecast, DATE, TimeCondition.DAWN, Set.of(WeatherCondition.CLEAR)))
                            .isFalse()
            ).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("matchesAnyTime - 시간대 없는 출사알림(프리뷰 폴백)")
    class MatchesAnyTime {

        @Test
        @DisplayName("그날 아무 시간대 슬롯이라도 조건에 맞으면 true (시간대 무시)")
        void anySlotOfTheDayMatches() {
            List<WeatherForecastResponse> forecast = List.of(
                    f(DATE, "0300", "RAINY"),
                    f(DATE, "2200", "CLEAR")  // 어느 시각이든 맞으면 됨
            );
            assertThat(sut.matchesAnyTime(forecast, DATE, Set.of(WeatherCondition.CLEAR))).isTrue();
        }

        @Test
        @DisplayName("그날 어떤 슬롯도 조건에 안 맞으면 false")
        void noSlotMatchesReturnsFalse() {
            List<WeatherForecastResponse> forecast = List.of(
                    f(DATE, "0500", "RAINY"),
                    f(DATE, "1400", "RAINY")
            );
            assertThat(sut.matchesAnyTime(forecast, DATE, Set.of(WeatherCondition.CLEAR))).isFalse();
        }

        @Test
        @DisplayName("NONE이면 true, 조건이 비면 false")
        void noneAndEmpty() {
            List<WeatherForecastResponse> forecast = List.of(f(DATE, "0500", "RAINY"));
            assertThat(sut.matchesAnyTime(forecast, DATE, Set.of(WeatherCondition.NONE))).isTrue();
            assertThat(sut.matchesAnyTime(forecast, DATE, Set.of())).isFalse();
        }

        @Test
        @DisplayName("다른 날짜의 슬롯은 무시한다")
        void ignoresOtherDates() {
            List<WeatherForecastResponse> forecast = List.of(f(OTHER_DATE, "1400", "CLEAR"));
            assertThat(sut.matchesAnyTime(forecast, DATE, Set.of(WeatherCondition.CLEAR))).isFalse();
        }
    }
}
