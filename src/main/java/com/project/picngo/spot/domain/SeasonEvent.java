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

    @Builder
    public SeasonEvent(String name, String monthDayStart, String monthDayPeakStart,
                       String monthDayPeakEnd, String monthDayEnd,
                       String region, Integer maxScore, Boolean isActive) {
        this.name = name;
        this.monthDayStart = monthDayStart;
        this.monthDayPeakStart = monthDayPeakStart;
        this.monthDayPeakEnd = monthDayPeakEnd;
        this.monthDayEnd = monthDayEnd;
        this.region = region;
        this.maxScore = maxScore;
        this.isActive = isActive;
    }
}
