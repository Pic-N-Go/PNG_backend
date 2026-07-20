package com.project.picngo.external;

import java.util.Map;

// 카카오 region_1depth_name → 에어코리아 sidoName 정규화.
// NotificationScheduler에 동일 하드코딩이 남아있음 → 후속으로 이 매퍼로 교체 권장.
public final class SidoNameMapper {

    private SidoNameMapper() {}

    // substring(0,2)로는 잘못 나오는(충청/전라/경상) 도 단위만 명시 매핑
    private static final Map<String, String> OVERRIDES = Map.of(
            "충청북도", "충북",
            "충청남도", "충남",
            "전라북도", "전북",
            "전라남도", "전남",
            "경상북도", "경북",
            "경상남도", "경남"
    );

    public static String normalize(String region1depthName) {
        if (region1depthName == null || region1depthName.length() < 2) {
            return null;
        }
        String override = OVERRIDES.get(region1depthName);
        return override != null ? override : region1depthName.substring(0, 2);
    }
}
