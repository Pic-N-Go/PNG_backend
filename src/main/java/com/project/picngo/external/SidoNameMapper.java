package com.project.picngo.external;

import java.util.Map;

// 카카오 region_1depth_name("충청북도") 또는 스팟 전체 주소("충청북도 청주시 ...") → 에어코리아 sidoName("충북") 정규화.
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
        // 전체 주소도 받으므로 정확일치가 아닌 prefix 매칭 (OVERRIDES 6개뿐이라 순회로 충분)
        for (Map.Entry<String, String> entry : OVERRIDES.entrySet()) {
            if (region1depthName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return region1depthName.substring(0, 2);
    }
}
