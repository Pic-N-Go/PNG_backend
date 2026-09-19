package com.project.picngo.external;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 한국관광공사 TourAPI 지역코드(areaCode: 1~39)와
 * 법정동 시도 코드(lDongRegnCd: 11~50) 간의 양방향 매핑 유틸리티.
 */
public final class LegalDongMapper {

    private LegalDongMapper() {}

    private static final Map<Integer, Integer> AREA_TO_LDONG = new LinkedHashMap<>();
    private static final Map<Integer, Integer> LDONG_TO_AREA = new LinkedHashMap<>();
    private static final Map<Integer, String> LDONG_TO_NAME = new LinkedHashMap<>();

    static {
        register(1, 11, "서울특별시");
        register(2, 28, "인천광역시");
        register(3, 30, "대전광역시");
        register(4, 27, "대구광역시");
        register(5, 29, "광주광역시");
        register(6, 26, "부산광역시");
        register(7, 31, "울산광역시");
        register(8, 36, "세종특별자치시");
        register(31, 41, "경기도");
        register(32, 51, "강원특별자치도");
        register(33, 43, "충청북도");
        register(34, 44, "충청남도");
        register(35, 52, "전북특별자치도");
        register(36, 46, "전라남도");
        register(37, 47, "경상북도");
        register(38, 48, "경상남도");
        register(39, 50, "제주특별자치도");
    }

    private static void register(int areaCode, int ldongCode, String name) {
        AREA_TO_LDONG.put(areaCode, ldongCode);
        LDONG_TO_AREA.put(ldongCode, areaCode);
        LDONG_TO_NAME.put(ldongCode, name);
    }

    public static Integer toLdongRegnCd(Integer areaCode) {
        if (areaCode == null) return null;
        return AREA_TO_LDONG.get(areaCode);
    }

    public static Integer toAreaCode(Integer ldongRegnCd) {
        if (ldongRegnCd == null) return null;
        // 구 강원도(42), 구 전라북도(45) 호환
        if (ldongRegnCd == 42) return 32;
        if (ldongRegnCd == 45) return 35;
        return LDONG_TO_AREA.get(ldongRegnCd);
    }

    public static String getRegionName(Integer ldongRegnCd) {
        if (ldongRegnCd == null) return "전체";
        if (ldongRegnCd == 42) return "강원특별자치도";
        if (ldongRegnCd == 45) return "전북특별자치도";
        return LDONG_TO_NAME.getOrDefault(ldongRegnCd, "지역(" + ldongRegnCd + ")");
    }

    public static List<Integer> getAllLdongRegnCodes() {
        return List.copyOf(LDONG_TO_NAME.keySet());
    }
}
