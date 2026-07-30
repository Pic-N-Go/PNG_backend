package com.project.picngo.spotalert.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.project.picngo.spotalert.domain.enums.TimeCondition;
import com.project.picngo.spotalert.domain.enums.WeatherCondition;
import com.project.picngo.spotalert.domain.enums.AirQualityCondition;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpotAlert extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long spotId;
    
    @Column(length = 200)
    private String memo;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "spot_alert_weather_conditions", joinColumns = @JoinColumn(name = "spot_alert_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "weather_condition", length = 50)
    private Set<WeatherCondition> weatherConditions = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "spot_alert_time_conditions", joinColumns = @JoinColumn(name = "spot_alert_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "time_condition", length = 50)
    private Set<TimeCondition> timeConditions = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "air_quality_condition", length = 50)
    private AirQualityCondition airQualityCondition = AirQualityCondition.NONE;

    @Column(nullable = false)
    private Integer alertTimingDays = 0;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Builder
    public SpotAlert(Long userId, Long spotId, String memo, Set<WeatherCondition> weatherConditions, Set<TimeCondition> timeConditions, AirQualityCondition airQualityCondition, Integer alertTimingDays, Boolean isActive) {
        this.userId = userId;
        this.spotId = spotId;
        this.memo = memo;
        this.weatherConditions = weatherConditions != null ? weatherConditions : new HashSet<>();
        this.timeConditions = timeConditions != null ? timeConditions : new HashSet<>();
        this.airQualityCondition = airQualityCondition != null ? airQualityCondition : AirQualityCondition.NONE;
        this.alertTimingDays = alertTimingDays != null ? alertTimingDays : 0;
        this.isActive = isActive != null ? isActive : true;
    }

    public void updateSettings(String memo, Set<WeatherCondition> weatherConditions, Set<TimeCondition> timeConditions, AirQualityCondition airQualityCondition, Integer alertTimingDays, Boolean isActive) {
        this.memo = memo;
        if (weatherConditions != null) {
            this.weatherConditions.clear();
            this.weatherConditions.addAll(weatherConditions);
        }
        if (timeConditions != null) {
            this.timeConditions.clear();
            this.timeConditions.addAll(timeConditions);
        }
        this.airQualityCondition = airQualityCondition != null ? airQualityCondition : AirQualityCondition.NONE;
        this.alertTimingDays = alertTimingDays != null ? alertTimingDays : 0;
        this.isActive = isActive != null ? isActive : true;
    }
}
