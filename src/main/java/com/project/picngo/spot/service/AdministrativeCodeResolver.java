package com.project.picngo.spot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 스팟의 주소 문자열로부터 공공데이터포털 / 한국관광공사 연관관광지 API에서 요구하는
 * 2자리 시도 코드(areaCd) 및 5자리 시군구 코드(signguCd)를 산출하는 리졸버입니다.
 */
@Slf4j
@Component
public class AdministrativeCodeResolver {

    public record AdministrativeCode(String areaCd, String signguCd) {}

    private static final Map<String, AdministrativeCode> CODE_MAP = new HashMap<>();
    private static final Map<String, String> SIDO_CODE_MAP = new HashMap<>();

    static {
        // 광역 시도 기본 매핑 (2자리)
        SIDO_CODE_MAP.put("서울", "11");
        SIDO_CODE_MAP.put("서울특별시", "11");
        SIDO_CODE_MAP.put("부산", "26");
        SIDO_CODE_MAP.put("부산광역시", "26");
        SIDO_CODE_MAP.put("대구", "27");
        SIDO_CODE_MAP.put("대구광역시", "27");
        SIDO_CODE_MAP.put("인천", "28");
        SIDO_CODE_MAP.put("인천광역시", "28");
        SIDO_CODE_MAP.put("광주", "29");
        SIDO_CODE_MAP.put("광주광역시", "29");
        SIDO_CODE_MAP.put("대전", "30");
        SIDO_CODE_MAP.put("대전광역시", "30");
        SIDO_CODE_MAP.put("울산", "31");
        SIDO_CODE_MAP.put("울산광역시", "31");
        SIDO_CODE_MAP.put("세종", "36");
        SIDO_CODE_MAP.put("세종특별자치시", "36");
        SIDO_CODE_MAP.put("경기", "41");
        SIDO_CODE_MAP.put("경기도", "41");
        SIDO_CODE_MAP.put("강원", "51");
        SIDO_CODE_MAP.put("강원특별자치도", "51");
        SIDO_CODE_MAP.put("충북", "43");
        SIDO_CODE_MAP.put("충청북도", "43");
        SIDO_CODE_MAP.put("충남", "44");
        SIDO_CODE_MAP.put("충청남도", "44");
        SIDO_CODE_MAP.put("전북", "52");
        SIDO_CODE_MAP.put("전북특별자치도", "52");
        SIDO_CODE_MAP.put("전라북도", "52");
        SIDO_CODE_MAP.put("전남", "46");
        SIDO_CODE_MAP.put("전라남도", "46");
        SIDO_CODE_MAP.put("경북", "47");
        SIDO_CODE_MAP.put("경상북도", "47");
        SIDO_CODE_MAP.put("경남", "48");
        SIDO_CODE_MAP.put("경상남도", "48");
        SIDO_CODE_MAP.put("제주", "50");
        SIDO_CODE_MAP.put("제주특별자치도", "50");

        // 충청남도 (현재 DB 다수 스팟 보유 지역)
        putCode("충남", "태안", "44", "44825");
        putCode("충남", "보령", "44", "44180");
        putCode("충남", "서산", "44", "44210");
        putCode("충남", "당진", "44", "44270");
        putCode("충남", "천안", "44", "44131");
        putCode("충남", "공주", "44", "44150");
        putCode("충남", "아산", "44", "44200");
        putCode("충남", "논산", "44", "44230");
        putCode("충남", "계룡", "44", "44250");
        putCode("충남", "금산", "44", "44710");
        putCode("충남", "부여", "44", "44760");
        putCode("충남", "서천", "44", "44770");
        putCode("충남", "청양", "44", "44790");
        putCode("충남", "홍성", "44", "44800");
        putCode("충남", "예산", "44", "44810");

        // 서울특별시 (현재 DB 다수 스팟 보유 지역)
        putCode("서울", "종로구", "11", "11110");
        putCode("서울", "중구", "11", "11140");
        putCode("서울", "용산구", "11", "11170");
        putCode("서울", "성동구", "11", "11200");
        putCode("서울", "광진구", "11", "11215");
        putCode("서울", "동대문구", "11", "11230");
        putCode("서울", "중랑구", "11", "11260");
        putCode("서울", "성북구", "11", "11290");
        putCode("서울", "강북구", "11", "11305");
        putCode("서울", "도봉구", "11", "11320");
        putCode("서울", "노원구", "11", "11350");
        putCode("서울", "은평구", "11", "11380");
        putCode("서울", "서대문구", "11", "11410");
        putCode("서울", "마포구", "11", "11440");
        putCode("서울", "양천구", "11", "11470");
        putCode("서울", "강서구", "11", "11500");
        putCode("서울", "구로구", "11", "11530");
        putCode("서울", "금천구", "11", "11545");
        putCode("서울", "영등포구", "11", "11560");
        putCode("서울", "동작구", "11", "11590");
        putCode("서울", "관악구", "11", "11620");
        putCode("서울", "서초구", "11", "11650");
        putCode("서울", "강남구", "11", "11680");
        putCode("서울", "송파구", "11", "11710");
        putCode("서울", "강동구", "11", "11740");

        // 인천광역시
        putCode("인천", "중구", "28", "28110");
        putCode("인천", "동구", "28", "28140");
        putCode("인천", "미추홀구", "28", "28177");
        putCode("인천", "연수구", "28", "28185");
        putCode("인천", "남동구", "28", "28200");
        putCode("인천", "부평구", "28", "28237");
        putCode("인천", "계양구", "28", "28245");
        putCode("인천", "서구", "28", "28260");
        putCode("인천", "강화군", "28", "28710");
        putCode("인천", "옹진군", "28", "28720");

        // 강원특별자치도
        putCode("강원", "원주", "51", "51130");
        putCode("강원", "춘천", "51", "51110");
        putCode("강원", "강릉", "51", "51150");
        putCode("강원", "동해", "51", "51170");
        putCode("강원", "태백", "51", "51190");
        putCode("강원", "속초", "51", "51210");
        putCode("강원", "삼척", "51", "51230");
        putCode("강원", "홍천", "51", "51720");
        putCode("강원", "횡성", "51", "51730");
        putCode("강원", "영월", "51", "51750");
        putCode("강원", "평창", "51", "51760");
        putCode("강원", "정선", "51", "51770");
        putCode("강원", "철원", "51", "51780");
        putCode("강원", "화천", "51", "51790");
        putCode("강원", "양구", "51", "51800");
        putCode("강원", "인제", "51", "51810");
        putCode("강원", "고성", "51", "51820");
        putCode("강원", "양양", "51", "51830");

        // 경기도
        putCode("경기", "수원", "41", "41110");
        putCode("경기", "성남", "41", "41130");
        putCode("경기", "의정부", "41", "41150");
        putCode("경기", "안양", "41", "41170");
        putCode("경기", "부천", "41", "41190");
        putCode("경기", "광명", "41", "41210");
        putCode("경기", "평택", "41", "41220");
        putCode("경기", "동두천", "41", "41250");
        putCode("경기", "안산", "41", "41270");
        putCode("경기", "고양", "41", "41280");
        putCode("경기", "과천", "41", "41290");
        putCode("경기", "구리", "41", "41310");
        putCode("경기", "남양주", "41", "41360");
        putCode("경기", "오산", "41", "41370");
        putCode("경기", "시흥", "41", "41390");
        putCode("경기", "군포", "41", "41410");
        putCode("경기", "의왕", "41", "41430");
        putCode("경기", "하남", "41", "41450");
        putCode("경기", "용인", "41", "41460");
        putCode("경기", "파주", "41", "41480");
        putCode("경기", "이천", "41", "41500");
        putCode("경기", "안성", "41", "41550");
        putCode("경기", "김포", "41", "41570");
        putCode("경기", "화성", "41", "41590");
        putCode("경기", "광주", "41", "41610");
        putCode("경기", "양주", "41", "41630");
        putCode("경기", "포천", "41", "41650");
        putCode("경기", "여주", "41", "41670");
        putCode("경기", "연천", "41", "41800");
        putCode("경기", "가평", "41", "41820");
        putCode("경기", "양평", "41", "41830");

        // 제주, 부산, 경북, 전남, 전북 등 주요 관광지
        putCode("제주", "제주", "50", "50110");
        putCode("제주", "서귀포", "50", "50130");
        putCode("부산", "해운대", "26", "26350");
        putCode("부산", "수영", "26", "26500");
        putCode("부산", "기장", "26", "26710");
        putCode("경북", "경주", "47", "47130");
        putCode("경북", "포항", "47", "47110");
        putCode("경북", "안동", "47", "47170");
        putCode("전남", "여수", "46", "46130");
        putCode("전남", "순천", "46", "46150");
        putCode("전북", "전주", "52", "52110");
        putCode("전북", "군산", "52", "52130");
        putCode("광주", "동구", "29", "29110");
        putCode("광주", "서구", "29", "29140");
        putCode("광주", "남구", "29", "29155");
        putCode("광주", "북구", "29", "29170");
        putCode("광주", "광산구", "29", "29200");
        putCode("세종", "세종", "36", "36110");
    }

    private static void putCode(String sido, String sigungu, String areaCd, String signguCd) {
        AdministrativeCode code = new AdministrativeCode(areaCd, signguCd);
        CODE_MAP.put(sido + "_" + sigungu, code);
        CODE_MAP.put(sigungu, code);
    }

    public Optional<AdministrativeCode> resolve(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }

        String trimmed = address.trim();
        String[] tokens = trimmed.split("\\s+");

        String sidoToken = tokens.length > 0 ? tokens[0] : "";
        String sigunguToken = tokens.length > 1 ? tokens[1] : "";

        // 1. 시도_시군구 복합 키 매칭
        for (Map.Entry<String, AdministrativeCode> entry : CODE_MAP.entrySet()) {
            String key = entry.getKey();
            if (key.contains("_")) {
                String[] parts = key.split("_");
                if (sidoToken.contains(parts[0]) && sigunguToken.contains(parts[1])) {
                    return Optional.of(entry.getValue());
                }
            }
        }

        // 2. 시군구 단독 매칭 (예: "태안군", "보령시", "원주시")
        for (Map.Entry<String, AdministrativeCode> entry : CODE_MAP.entrySet()) {
            String key = entry.getKey();
            if (!key.contains("_") && (sigunguToken.contains(key) || trimmed.contains(key))) {
                return Optional.of(entry.getValue());
            }
        }

        // 3. 광역 시도 기반 폴백
        for (Map.Entry<String, String> entry : SIDO_CODE_MAP.entrySet()) {
            if (sidoToken.contains(entry.getKey())) {
                String areaCd = entry.getValue();
                return Optional.of(new AdministrativeCode(areaCd, areaCd + "000"));
            }
        }

        return Optional.empty();
    }
}
