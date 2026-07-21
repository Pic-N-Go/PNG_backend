package com.project.picngo.common.util;

/**
 * 기상청 단기예보 OpenAPI 통신을 위한 위경도 -> 격자 X, Y 변환 유틸리티
 */
public class LatXLngYConverter {

    public static class LatXLngY {
        public double lat;
        public double lng;
        public int x;
        public int y;

        public LatXLngY() {}

        public LatXLngY(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    // 제주도 공식 KMA 격자 리스트 (엑셀 기준)
    private static final int[][] VALID_JEJU_GRIDS = {
            {53, 38}, {48, 36}, {49, 37}, {59, 38}, {55, 39}, {46, 35}, {48, 48}, {60, 38}, {54, 38}, {52, 38}, {51, 38}, // 제주시
            {53, 33}, {48, 32}, {56, 33}, {60, 37}, {49, 32}, {58, 34}, {54, 33}, {52, 32}, {51, 32}, {50, 32}  // 서귀포시
    };

    /**
     * 계산된 (x, y)가 제주도 바운더리 내에 있을 경우,
     * 가장 가까운 기상청 공식 행정구역 격자 좌표로 보정(Snap)합니다.
     */
    private static void snapToNearestValidJejuGrid(LatXLngY rs) {
        // 제주도 대략적인 바운더리 (추자도 포함)
        if (rs.x >= 45 && rs.x <= 65 && rs.y >= 30 && rs.y <= 50) {
            int bestX = rs.x;
            int bestY = rs.y;
            double minDist = Double.MAX_VALUE;

            for (int[] grid : VALID_JEJU_GRIDS) {
                int vx = grid[0];
                int vy = grid[1];
                if (rs.x == vx && rs.y == vy) return; // 이미 유효한 격자면 통과

                double dist = Math.pow(rs.x - vx, 2) + Math.pow(rs.y - vy, 2);
                if (dist < minDist) {
                    minDist = dist;
                    bestX = vx;
                    bestY = vy;
                }
            }
            rs.x = bestX;
            rs.y = bestY;
        }
    }

    public static LatXLngY convertGrid(double lat_X, double lng_Y) {
        double RE = 6371.00877; // 지구 반경(km)
        double GRID = 5.0; // 격자 간격(km)
        double SLAT1 = 30.0; // 투영 위도1(degree)
        double SLAT2 = 60.0; // 투영 위도2(degree)
        double OLON = 126.0; // 기준점 경도(degree)
        double OLAT = 38.0; // 기준점 위도(degree)
        double XO = 43; // 기준점 X좌표(GRID)
        double YO = 136; // 기기준점 Y좌표(GRID)

        double DEGRAD = Math.PI / 180.0;
        double re = RE / GRID;
        double slat1 = SLAT1 * DEGRAD;
        double slat2 = SLAT2 * DEGRAD;
        double olon = OLON * DEGRAD;
        double olat = OLAT * DEGRAD;

        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);

        LatXLngY rs = new LatXLngY();
        rs.lat = lat_X;
        rs.lng = lng_Y;
        double ra = Math.tan(Math.PI * 0.25 + (lat_X) * DEGRAD * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = lng_Y * DEGRAD - olon;
        if (theta > Math.PI) theta -= 2.0 * Math.PI;
        if (theta < -Math.PI) theta += 2.0 * Math.PI;
        theta *= sn;
        rs.x = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
        rs.y = (int) Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);

        // 공식 격자로 보정
        snapToNearestValidJejuGrid(rs);
        return rs;
    }
}
