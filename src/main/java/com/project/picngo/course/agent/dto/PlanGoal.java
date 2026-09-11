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
        return new PlanGoal(
                resolvedRegion,
                resolvedDate,
                resolvedDuration,
                List.of(SpotCategory.SUNRISE_SUNSET, SpotCategory.NIGHT_VIEW, SpotCategory.PARK),
                "감성 출사 추천 코스",
                List.of("노을", "야경", "산책")
        );
    }

    public static String extractRegionFromPrompt(String prompt, String defaultRegion) {
        if (defaultRegion != null && !defaultRegion.isBlank() && !defaultRegion.equals("미지정") && !defaultRegion.equals("전국")) {
            return defaultRegion;
        }
        if (prompt == null || prompt.isBlank()) {
            return "서울";
        }
        for (String r : List.of("제주", "부산", "강원", "강릉", "속초", "경주", "여수", "순천", "전주", "춘천", "대구", "인천", "광주", "대전", "울산", "수원", "포항", "통영", "거제", "남해")) {
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
