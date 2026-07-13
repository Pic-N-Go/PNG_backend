package com.project.picngo.spot.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // MM-dd 형식 (예: "03-20")
    private String monthDayStart;
    private String monthDayPeakStart;
    private String monthDayPeakEnd;
    private String monthDayEnd;

    private String region; // null = 전국

    private Integer maxScore;

    private Boolean isActive;

    // TourAPI cat3 코드 콤마 구분 (예: "A02010700,A02010800"). null/빈 문자열/공백 = 카테고리 무관 전체 적용
    private String eligibleCat3;

    @Builder
    public SeasonEvent(String name, String monthDayStart, String monthDayPeakStart,
                       String monthDayPeakEnd, String monthDayEnd,
                       String region, Integer maxScore, Boolean isActive, String eligibleCat3) {
        this.name = name;
        this.monthDayStart = monthDayStart;
        this.monthDayPeakStart = monthDayPeakStart;
        this.monthDayPeakEnd = monthDayPeakEnd;
        this.monthDayEnd = monthDayEnd;
        this.region = region;
        this.maxScore = maxScore;
        this.isActive = isActive;
        this.eligibleCat3 = eligibleCat3;
    }

    public boolean isEligibleForCat3(String cat3) {
        // 빈 문자열/공백도 null과 동일하게 "제한 없음"으로 처리 — 실수로 ''가 들어가도 전체 적용이 안전한 기본값
        if (eligibleCat3 == null || eligibleCat3.isBlank()) return true;
        if (cat3 == null) return false;
        for (String code : eligibleCat3.split(",")) {
            if (code.trim().equals(cat3)) return true;
        }
        return false;
    }
}
