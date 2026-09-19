package com.project.picngo.spot.service;

import java.util.Map;

public final class TourAreaCodeMapper {
    private static final Map<Integer, Integer> LEGAL_REGION_CODES = Map.ofEntries(
            Map.entry(1,11), Map.entry(2,28), Map.entry(3,30), Map.entry(4,27),
            Map.entry(5,29), Map.entry(6,26), Map.entry(7,31), Map.entry(8,36),
            Map.entry(31,41), Map.entry(32,51), Map.entry(33,43), Map.entry(34,44),
            Map.entry(35,47), Map.entry(36,48), Map.entry(37,52), Map.entry(38,46), Map.entry(39,50)
    );
    private TourAreaCodeMapper() {}
    public static Integer toLegalRegionCode(Integer areaCode) {
        if (areaCode == null) return null;
        Integer code = LEGAL_REGION_CODES.get(areaCode);
        if (code == null) throw new IllegalArgumentException("지원하지 않는 TourAPI 지역 코드: " + areaCode);
        return code;
    }
}
