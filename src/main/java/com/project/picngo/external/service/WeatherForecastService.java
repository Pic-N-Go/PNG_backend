package com.project.picngo.external.service;

import com.project.picngo.external.WeatherClient;
import com.project.picngo.external.dto.KmaMidTaApiResponse;
import com.project.picngo.external.dto.KmaMidWeatherApiResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherForecastService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WeatherClient weatherClient;

    /**
     * 단기예보(가까운 날)가 "완전한 것"으로 인정되려면 최소 이만큼의 서로 다른 날짜를 가져야 한다.
     * 정상 응답(어제 2300 발표)은 어제/오늘/내일/모레를 포함하므로 넉넉히 충족한다.
     * 이 값을 못 채우면 단기예보가 반쪽으로 온 것이므로 캐시에 저장하지 않는다(구멍 뚫린 예보 박제 방지).
     */
    private static final int MIN_SHORT_TERM_DISTINCT_DATES = 3;

    /**
     * 단기예보와 중기예보의 병합 결과.
     * <p>
     * {@code shortTermMerged}는 단기예보(가까운 날)가 충분한 날짜 범위로 병합됐는지,
     * {@code midTermMerged}는 중기예보(D+3 이후)가 병합됐는지를 나타낸다.
     * 둘 중 하나라도 false면 예보에 구멍이 뚫린 것이므로({@link #complete()} == false)
     * 호출부가 캐시 저장을 건너뛰어야 한다. 어느 한쪽만 반쪽으로 캐시되면 TTL 동안 그 구멍이 박제된다.
     */
    public record CombinedForecastResult(List<WeatherForecastResponse> forecasts,
                                         boolean shortTermMerged, boolean midTermMerged) {
        public boolean complete() {
            return shortTermMerged && midTermMerged;
        }
    }

    /**
     * 단기예보(1~3일)와 중기예보(3~7일)를 병합하여 반환합니다.
     */
    public List<WeatherForecastResponse> getCombined7DayForecast(Double lat, Double lng, String date) {
        return getCombined7DayForecastResult(lat, lng, date).forecasts();
    }

    /**
     * 단기+중기 병합 결과를 병합 성공 여부와 함께 반환합니다.
     * 단기·중기 어느 한쪽이라도 반쪽으로 온 결과는 호출부가 캐시 저장을 건너뛸 수 있도록 플래그로 구분합니다.
     */
    public CombinedForecastResult getCombined7DayForecastResult(Double lat, Double lng, String date) {
        List<WeatherForecastResponse> combined = new ArrayList<>();
        Set<String> existingSlots = new HashSet<>();
        boolean shortTermMerged = false;
        boolean midTermMerged = false;

        try {
            // 1. 단기예보 (시간별)
            List<WeatherForecastResponse> shortTerm = weatherClient.getShortTermForecast(lat, lng, date);
            if (shortTerm != null && !shortTerm.isEmpty()) {
                for (WeatherForecastResponse st : shortTerm) {
                    combined.add(st);
                    existingSlots.add(st.date() + "_" + st.time());
                }
                // 가까운 날이 통째로 비지 않았는지(반쪽 응답이 아닌지) 날짜 범위로 확인한다.
                long distinctDates = shortTerm.stream().map(WeatherForecastResponse::date).distinct().count();
                shortTermMerged = distinctDates >= MIN_SHORT_TERM_DISTINCT_DATES;
                if (!shortTermMerged) {
                    log.warn("단기예보가 반쪽으로 수신됨(서로 다른 날짜 {}개) - 캐시 저장 대상에서 제외", distinctDates);
                }
            } else {
                log.warn("단기예보 응답이 비어 있음 - 캐시 저장 대상에서 제외");
            }
        } catch (Exception e) {
            log.warn("단기예보 병합 실패: {}", e.getMessage());
        }

        try {
            // 2. 중기예보 (일별) - 최신 유효 발표시각(tmFc) 동적 계산
            LocalDateTime now = LocalDateTime.now(KST);
            String tmFc = calculateLatestMidTermTmFc(now);
            String regId = getMidTermRegionCode(lat, lng);

            KmaMidWeatherApiResponse midTermResponse = weatherClient.getMidTermForecast(regId, tmFc);

            if (midTermResponse != null && midTermResponse.response() != null
                    && midTermResponse.response().body() != null
                    && midTermResponse.response().body().items() != null
                    && midTermResponse.response().body().items().item() != null
                    && !midTermResponse.response().body().items().item().isEmpty()) {

                KmaMidWeatherApiResponse.Item item = midTermResponse.response().body().items().item().get(0);
                LocalDate fcstBaseDate = LocalDate.parse(tmFc.substring(0, 8), DATE_FMT);

                // 중기 기온예보(getMidTa)는 best-effort로 조회한다.
                // 육상예보(날씨상태)가 병합된 이상 기온 조회가 실패해도 병합 자체는 성공으로 본다.
                KmaMidTaApiResponse.Item taItem = fetchMidTermTemperature(regId, tmFc);

                // 중기 예보는 발표일 기준 Day 3 ~ Day 7
                // (D+3 등 단기예보에 특정 시간대만 잘려 있는 경우, 누락된 아침/점심/저녁 슬롯을 중기예보가 보완)
                addMidTermIfNotPresent(combined, existingSlots, extractMidTerm(fcstBaseDate, 3, item.wf3Am(), item.wf3Pm(), taItem));
                addMidTermIfNotPresent(combined, existingSlots, extractMidTerm(fcstBaseDate, 4, item.wf4Am(), item.wf4Pm(), taItem));
                addMidTermIfNotPresent(combined, existingSlots, extractMidTerm(fcstBaseDate, 5, item.wf5Am(), item.wf5Pm(), taItem));
                addMidTermIfNotPresent(combined, existingSlots, extractMidTerm(fcstBaseDate, 6, item.wf6Am(), item.wf6Pm(), taItem));
                addMidTermIfNotPresent(combined, existingSlots, extractMidTerm(fcstBaseDate, 7, item.wf7Am(), item.wf7Pm(), taItem));

                midTermMerged = true;
            }

        } catch (Exception e) {
            log.warn("중기예보 병합 실패: {}", e.getMessage());
        }

        return new CombinedForecastResult(combined, shortTermMerged, midTermMerged);
    }

    /** 중기 기온예보를 조회한다. 실패하거나 데이터가 없으면 null(기온 없이 날씨상태만 병합). */
    private KmaMidTaApiResponse.Item fetchMidTermTemperature(String landRegId, String tmFc) {
        try {
            KmaMidTaApiResponse taResponse = weatherClient.getMidTermTemperature(getMidTaRegionCode(landRegId), tmFc);
            if (taResponse != null && taResponse.response() != null
                    && taResponse.response().body() != null
                    && taResponse.response().body().items() != null
                    && taResponse.response().body().items().item() != null
                    && !taResponse.response().body().items().item().isEmpty()) {
                return taResponse.response().body().items().item().get(0);
            }
        } catch (Exception e) {
            log.warn("중기 기온예보 조회 실패(날씨상태만 병합): {}", e.getMessage());
        }
        return null;
    }

    private void addMidTermIfNotPresent(List<WeatherForecastResponse> combined, Set<String> existingSlots, List<WeatherForecastResponse> midTerms) {
        for (WeatherForecastResponse mt : midTerms) {
            String slotKey = mt.date() + "_" + mt.time();
            if (!existingSlots.contains(slotKey)) {
                combined.add(mt);
                existingSlots.add(slotKey);
            }
        }
    }

    /**
     * 기상청 중기육상예보는 1일 2회(06:00, 18:00) 발표되며 최근 24시간 이내 데이터만 조회 가능합니다.
     * 공공데이터포털 반영 지연(약 20~30분)을 고려해 06:30, 18:30을 전환 시점으로 적용합니다.
     */
    public String calculateLatestMidTermTmFc(LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        if (time.isBefore(LocalTime.of(6, 30))) {
            // 06:30 이전: 전일 18시 발표
            return date.minusDays(1).format(DATE_FMT) + "1800";
        } else if (time.isBefore(LocalTime.of(18, 30))) {
            // 06:30 ~ 18:30: 당일 06시 발표
            return date.format(DATE_FMT) + "0600";
        } else {
            // 18:30 이후: 당일 18시 발표
            return date.format(DATE_FMT) + "1800";
        }
    }

    private List<WeatherForecastResponse> extractMidTerm(LocalDate baseDate, int plusDays, String wfAm, String wfPm, KmaMidTaApiResponse.Item taItem) {
        List<WeatherForecastResponse> list = new ArrayList<>();
        String targetDate = baseDate.plusDays(plusDays).format(DATE_FMT);

        // 중기예보는 시간대별 기온이 없고 일자별 최저/최고만 준다.
        // 아침(1000)≈최저, 점심(1400)≈최고, 저녁(1800)≈최저·최고의 중간으로 근사한다.
        Integer taMin = midTaMin(taItem, plusDays);
        Integer taMax = midTaMax(taItem, plusDays);
        Double morningTemp = taMin != null ? taMin.doubleValue() : null;
        Double afternoonTemp = taMax != null ? taMax.doubleValue() : null;
        Double eveningTemp = (taMin != null && taMax != null)
                ? (double) Math.round((taMin + taMax) / 2.0)
                : null;

        // 오전(AM)은 1000으로 매핑
        if (wfAm != null && !wfAm.isBlank()) {
            list.add(new WeatherForecastResponse(targetDate, "1000", mapWfToStatus(wfAm), morningTemp));
        }
        // 오후(PM)는 1400, 1800으로 매핑
        if (wfPm != null && !wfPm.isBlank()) {
            list.add(new WeatherForecastResponse(targetDate, "1400", mapWfToStatus(wfPm), afternoonTemp));
            list.add(new WeatherForecastResponse(targetDate, "1800", mapWfToStatus(wfPm), eveningTemp));
        }

        return list;
    }

    /** 발표일 기준 D+plusDays(3~7)의 최저기온을 중기기온예보 항목에서 꺼낸다. 없으면 null. */
    private Integer midTaMin(KmaMidTaApiResponse.Item ta, int plusDays) {
        if (ta == null) return null;
        return switch (plusDays) {
            case 3 -> ta.taMin3();
            case 4 -> ta.taMin4();
            case 5 -> ta.taMin5();
            case 6 -> ta.taMin6();
            case 7 -> ta.taMin7();
            default -> null;
        };
    }

    /** 발표일 기준 D+plusDays(3~7)의 최고기온을 중기기온예보 항목에서 꺼낸다. 없으면 null. */
    private Integer midTaMax(KmaMidTaApiResponse.Item ta, int plusDays) {
        if (ta == null) return null;
        return switch (plusDays) {
            case 3 -> ta.taMax3();
            case 4 -> ta.taMax4();
            case 5 -> ta.taMax5();
            case 6 -> ta.taMax6();
            case 7 -> ta.taMax7();
            default -> null;
        };
    }

    /**
     * 중기육상예보(getMidLandFcst) 구역코드를 중기기온예보(getMidTa) 지점코드로 변환합니다.
     * 두 API는 regId 체계가 달라(육상=광역구역, 기온=대표도시 지점) 그대로 넘기면 데이터가 비어 옵니다.
     * 각 육상구역의 대표 도시 지점코드로 매핑합니다.
     */
    public String getMidTaRegionCode(String landRegId) {
        if (landRegId == null) {
            return "11B10101"; // 기본값 서울
        }
        return switch (landRegId) {
            case "11B00000" -> "11B10101"; // 서울/인천/경기 -> 서울
            case "11D10000" -> "11D10301"; // 강원 영서 -> 춘천
            case "11D20000" -> "11D20501"; // 강원 영동 -> 강릉
            case "11C10000" -> "11C10301"; // 충북 -> 청주
            case "11C20000" -> "11C20401"; // 대전/세종/충남 -> 대전
            case "11F10000" -> "11F10201"; // 전북 -> 전주
            case "11F20000" -> "11F20501"; // 광주/전남 -> 광주
            case "11H10000" -> "11H10701"; // 대구/경북 -> 대구
            case "11H20000" -> "11H20201"; // 부산/울산/경남 -> 부산
            case "11G00000" -> "11G00201"; // 제주 -> 제주
            default -> "11B10101";
        };
    }

    private String mapWfToStatus(String wf) {
        if (wf == null) return "CLEAR";
        if (wf.contains("비") || wf.contains("소나기")) return "RAINY";
        if (wf.contains("눈")) return "SNOWY";
        if (wf.contains("흐림") || wf.contains("구름많음")) return "CLOUDY";
        return "CLEAR";
    }

    /**
     * 위도/경도를 기반으로 기상청 중기육상예보 구역코드(regId)를 매핑합니다.
     */
    public String getMidTermRegionCode(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return "11B00000"; // 기본값 서울/경기
        }

        // 1. 제주도
        if (lat < 34.0 || (lat < 34.4 && lng < 127.0)) {
            return "11G00000";
        }

        // 2. 남부 지방 (호남 / 영남)
        if (lat < 36.0) {
            if (lng < 127.7) {
                // 호남: 전남(11F20000), 전북(11F10000)
                return (lat < 35.3) ? "11F20000" : "11F10000";
            } else {
                // 영남: 경남/부산/울산(11H20000), 대구/경북(11H10000)
                return (lat < 35.6) ? "11H20000" : "11H10000";
            }
        }

        // 3. 중부 지방 (충청 / 경북 북부)
        if (lat < 37.0) {
            if (lng < 127.3) {
                return "11C20000"; // 대전, 세종, 충남
            } else if (lng < 128.3) {
                return "11C10000"; // 충북
            } else {
                return "11H10000"; // 경북 북부 (안동, 영주, 문경 등)
            }
        }

        // 4. 북부 지방 (강원 영동 / 강원 영서 / 수도권)
        // 강원 영동: 속초, 고성, 양양, 강릉, 동해, 삼척 등 태백산맥 동쪽 (lng >= 128.5)
        if (lng >= 128.5) {
            return "11D20000";
        }
        // 강원 영서: 춘천, 원주, 홍천, 횡성, 철원, 화천 등 (lng >= 127.5 또는 철원 등 북부 경도 >= 127.1)
        if (lng >= 127.5 || (lat >= 38.0 && lng >= 127.1)) {
            return "11D10000";
        }

        // 수도권 (서울, 인천, 경기)
        return "11B00000";
    }
}
