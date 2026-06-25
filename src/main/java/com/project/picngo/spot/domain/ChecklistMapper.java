package com.project.picngo.spot.domain;

import java.util.List;
import java.util.Map;

public class ChecklistMapper {

    private static final Map<String, List<String>> CAT_CHECKLIST = Map.ofEntries(
        // 자연 - 산/고원
        Map.entry("A01010100", List.of("등산화", "방수 재킷", "삼각대", "광각렌즈", "보조배터리")),
        Map.entry("A01010200", List.of("등산화", "방수 재킷", "삼각대", "광각렌즈", "보조배터리")),
        Map.entry("A01010300", List.of("등산화", "방수 재킷", "삼각대", "광각렌즈", "보조배터리")),
        Map.entry("A01010400", List.of("등산화", "방수 재킷", "삼각대", "광각렌즈", "보조배터리")),
        Map.entry("A01010500", List.of("등산화", "방수 재킷", "삼각대", "광각렌즈", "보조배터리")),
        Map.entry("A01010600", List.of("등산화", "방수 재킷", "삼각대", "광각렌즈", "보조배터리")),
        Map.entry("A01010700", List.of("등산화", "방수 재킷", "삼각대", "광각렌즈", "보조배터리")),
        // 자연 - 수목원/식물원
        Map.entry("A01011200", List.of("편한 신발", "광각렌즈", "삼각대", "매크로렌즈")),
        // 자연 - 폭포/계곡/강
        Map.entry("A01020100", List.of("ND 필터", "삼각대", "방수 재킷", "편한 신발", "광각렌즈")),
        Map.entry("A01020200", List.of("ND 필터", "삼각대", "방수 재킷", "편한 신발", "광각렌즈")),
        Map.entry("A01020300", List.of("ND 필터", "삼각대", "방수 재킷", "편한 신발", "광각렌즈")),
        Map.entry("A01020400", List.of("ND 필터", "삼각대", "방수 재킷", "편한 신발", "광각렌즈")),
        // 해수욕장
        Map.entry("A02010700", List.of("광각렌즈", "ND 필터", "삼각대", "편한 신발", "방수 케이스")),
        // 해안절경
        Map.entry("A02010800", List.of("광각렌즈", "ND 필터", "삼각대", "방수 재킷", "편한 신발")),
        // 섬
        Map.entry("A02011000", List.of("광각렌즈", "ND 필터", "삼각대", "방수 재킷", "편한 신발")),
        // 갯벌
        Map.entry("A02010400", List.of("방수 장화", "방수 재킷", "광각렌즈", "삼각대")),
        // 전망대
        Map.entry("A02010500", List.of("망원렌즈", "삼각대", "보조배터리", "편한 신발")),
        // 공원
        Map.entry("A02010600", List.of("편한 신발", "광각렌즈", "삼각대", "보조배터리")),
        // 도심/관광특구 (가회동성당 등)
        Map.entry("A02010900", List.of("편한 신발", "광각렌즈", "삼각대", "보조배터리")),
        // 관광단지
        Map.entry("A02010100", List.of("편한 신발", "광각렌즈", "삼각대", "보조배터리")),
        // 관광지
        Map.entry("A02010200", List.of("편한 신발", "광각렌즈", "삼각대", "보조배터리")),
        // 테마파크
        Map.entry("A02010300", List.of("편한 신발", "광각렌즈", "보조배터리", "삼각대")),
        // 역사/문화 - 사찰
        Map.entry("A02020200", List.of("편한 신발", "광각렌즈", "삼각대", "보조배터리")),
        // 역사/문화 - 고궁
        Map.entry("A02020300", List.of("편한 신발", "광각렌즈", "삼각대", "보조배터리")),
        // 역사/문화 - 고택/전통마을
        Map.entry("A02020600", List.of("편한 신발", "광각렌즈", "삼각대", "보조배터리")),
        // 역사/문화 - 기념관/박물관
        Map.entry("A02020700", List.of("편한 신발", "광각렌즈", "보조배터리")),
        // 체험 - 산촌/농어촌
        Map.entry("A02030100", List.of("편한 신발", "광각렌즈", "보조배터리", "삼각대")),
        // 산업관광 - 공장/공단
        Map.entry("A02040200", List.of("장갑", "보조배터리", "방수 재킷", "광각렌즈")),
        // 건축/조형물 - 근대건축물
        Map.entry("A02050100", List.of("편한 신발", "광각렌즈", "삼각대", "보조배터리")),
        // 건축/조형물 - 다리/강
        Map.entry("A02050200", List.of("ND 필터", "삼각대", "광각렌즈", "편한 신발")),
        // 건축/조형물 - 가로/공원
        Map.entry("A02050300", List.of("편한 신발", "광각렌즈", "삼각대", "보조배터리"))
    );

    private static final List<String> DEFAULT = List.of("삼각대", "광각렌즈", "보조배터리", "편한 신발");

    public static List<String> getChecklist(String cat3) {
        if (cat3 == null) return DEFAULT;
        return CAT_CHECKLIST.getOrDefault(cat3, DEFAULT);
    }
}
