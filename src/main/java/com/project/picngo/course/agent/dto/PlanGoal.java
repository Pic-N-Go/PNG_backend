package com.project.picngo.course.agent.dto;

import com.project.picngo.common.domain.SpotCategory;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PlanGoal(
        String region,
        LocalDate targetDate,
        int durationDays,
        List<SpotCategory> categories,
        String theme,
        List<String> keywords
) {
    public PlanGoal(String region, LocalDate targetDate, List<SpotCategory> categories, String theme, List<String> keywords) {
        this(region, targetDate, 1, categories, theme, keywords);
    }

    public static PlanGoal fallback(String prompt, String region, LocalDate targetDate) {
        String resolvedRegion = extractRegionFromPrompt(prompt, region);
        LocalDate resolvedDate = (targetDate != null) ? targetDate : LocalDate.now().plusDays(1);
        int resolvedDuration = parseDurationDays(prompt);
        List<SpotCategory> categories = extractCategoriesFromPrompt(prompt);
        String theme = resolvedRegion + " 감성 출사 추천 코스";
        return new PlanGoal(
                resolvedRegion,
                resolvedDate,
                resolvedDuration,
                categories,
                theme,
                List.of()
        );
    }

    public static List<SpotCategory> extractCategoriesFromPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return List.of(SpotCategory.PARK, SpotCategory.SUNRISE_SUNSET, SpotCategory.NIGHT_VIEW);
        }
        List<SpotCategory> result = new java.util.ArrayList<>();
        String lower = prompt.toLowerCase();

        if (containsKeyword(lower, "바다", "해변", "해수욕장", "해안", "바닷가", "서해", "동해", "남해", "갯벌", "모래사장")) {
            result.add(SpotCategory.BEACH);
        }
        if (containsKeyword(lower, "카페", "디저트", "찻집", "커피", "베이커리", "빵", "브런치")) {
            result.add(SpotCategory.CAFE);
        }
        if (containsKeyword(lower, "야경", "밤풍경", "야간경관", "드라이브", "달빛", "불빛", "밤바다")) {
            result.add(SpotCategory.NIGHT_VIEW);
        }
        if (containsKeyword(lower, "일출", "일몰", "노을", "해넘이", "해돋이", "낙조", "황혼", "여명")) {
            result.add(SpotCategory.SUNRISE_SUNSET);
        }
        if (containsKeyword(lower, "한옥", "전통", "고택", "민속마을", "한옥마을", "서원")) {
            result.add(SpotCategory.HANOK);
        }
        if (containsKeyword(lower, "숲", "휴양림", "수목원", "피톤치드", "메타세쿼이아", "자작나무", "숲길")) {
            result.add(SpotCategory.FOREST);
        }
        if (containsKeyword(lower, "등산", "산행", "트래킹", "봉우리", "계곡", "케이블카", "설악산", "한라산", "지리산", "북한산", "남산", "국립공원")) {
            result.add(SpotCategory.MOUNTAIN);
        }
        if (containsKeyword(lower, "역사", "유적지", "유적", "문화재", "사찰", "박물관", "미술관", "궁궐", "고궁", "성곽", "사적지")) {
            result.add(SpotCategory.HERITAGE);
        }
        if (containsKeyword(lower, "꽃", "벚꽃", "억새", "튤립", "단풍", "식물원", "유채", "연꽃", "갈대", "수국", "정원")) {
            result.add(SpotCategory.FLOWER);
        }
        if (containsKeyword(lower, "공원", "산책", "산책로", "힐링", "유원지", "호수공원", "습지")) {
            result.add(SpotCategory.PARK);
        }
        if (containsKeyword(lower, "도심", "거리", "벽화마을", "골목", "핫플", "시티", "골목길", "타운")) {
            result.add(SpotCategory.CITY);
        }
        if (containsKeyword(lower, "은하수", "별자리", "천문대", "별보기", "밤하늘")) {
            result.add(SpotCategory.MILKY_WAY);
        }
        if (containsKeyword(lower, "축제", "행사", "페스티벌", "마켓", "공연")) {
            result.add(SpotCategory.FESTIVAL);
        }

        if (result.isEmpty()) {
            return List.of(SpotCategory.PARK, SpotCategory.SUNRISE_SUNSET, SpotCategory.NIGHT_VIEW);
        }
        return result;
    }

    private static boolean containsKeyword(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    public static String extractRegionFromPrompt(String prompt, String defaultRegion) {
        if (defaultRegion != null && !defaultRegion.isBlank() && !defaultRegion.equals("미지정") && !defaultRegion.equals("전국")) {
            return defaultRegion;
        }
        if (prompt == null || prompt.isBlank()) {
            return "서울";
        }
        // 기초지자체(시/군)를 광역지자체(도/특별시)보다 우선 매칭하여 정확도 향상
        List<String> regionCandidates = List.of(
                "태안", "보령", "서산", "당진",
                "강릉", "속초", "양양", "춘천", "원주", "동해", "태백", "삼척", "홍천", "횡성", "영월", "평창", "정선", "철원", "화천", "양구", "인제",
                "경주", "안동", "포항", "김천", "구미", "영주", "영천", "상주", "문경", "경산", "의성", "청송", "영양", "영덕", "청도", "고령", "성주", "칠곡", "예천", "봉화", "울진", "울릉",
                "여수", "순천", "담양", "목포", "광양", "곡성", "구례", "고흥", "보성", "화순", "장흥", "강진", "해남", "영암", "무안", "함평", "영광", "장성", "완도", "진도", "신안",
                "전주", "군산", "단양", "남원", "정읍", "익산", "김제", "완주", "진안", "무주", "장수", "임실", "순창", "고창", "부안",
                "통영", "거제", "남해", "창원", "진주", "사천", "김해", "밀양", "양산", "의령", "함안", "창녕", "하동", "산청", "함양", "거창", "합천",
                "천안", "공주", "아산", "논산", "계룡", "금산", "부여", "서천", "청양", "홍성", "예산",
                "청주", "충주", "제천", "보은", "옥천", "영동", "증평", "진천", "괴산", "음성",
                "수원", "성남", "의정부", "안양", "부천", "광명", "평택", "동두천", "안산", "고양", "과천", "구리", "남양주", "오산", "시흥", "군포", "의왕", "하남", "용인", "파주", "이천", "안성", "김포", "화성", "양주", "포천", "여주", "연천", "가평", "양평",
                "부산", "대구", "인천", "광주", "대전", "울산", "세종", "서울",
                "제주특별자치도", "제주",
                "충청남도", "충청북도", "전라남도", "전라북도", "경상남도", "경상북도", "강원특별자치도",
                "충남", "충북", "전남", "전북", "경남", "경북", "강원", "경기"
        );
        for (String r : regionCandidates) {
            if (prompt.contains(r)) {
                return r;
            }
        }
        return "서울";
    }

    public static int parseDurationDays(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return 1;
        }

        // 정규식 매칭: "1박 2일", "2박3일" 등
        Matcher nightsDaysMatcher = Pattern.compile("(\\d+)\\s*박\\s*(\\d+)\\s*일").matcher(prompt);
        if (nightsDaysMatcher.find()) {
            try {
                int days = Integer.parseInt(nightsDaysMatcher.group(2));
                return Math.min(7, Math.max(1, days));
            } catch (NumberFormatException ignored) {}
        }

        // 정규식 매칭: "2일간", "3일동안", "2일 코스" 등
        Matcher daysMatcher = Pattern.compile("(\\d+)\\s*일(?:간|동안|\\s*코스|\\s*일정)").matcher(prompt);
        if (daysMatcher.find()) {
            try {
                int days = Integer.parseInt(daysMatcher.group(1));
                return Math.min(7, Math.max(1, days));
            } catch (NumberFormatException ignored) {}
        }

        // 단어 기반 매칭
        if (prompt.contains("당일") || prompt.contains("하루")) {
            return 1;
        }
        if (prompt.contains("이틀")) {
            return 2;
        }
        if (prompt.contains("사흘")) {
            return 3;
        }
        if (prompt.contains("나흘")) {
            return 4;
        }

        return 1;
    }
}
