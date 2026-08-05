package com.project.picngo.course.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseSpot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false)
    private Long spotId;

    @Column(nullable = false)
    private Integer dayNumber;

    @Column(nullable = false)
    private Integer sequenceOrder;

    @Column(columnDefinition = "TEXT")
    private String memo;

    private Integer travelTimeMinutes;

    // 위 이동시간이 카카오 실측이 아니라 자체 추정값인지 여부.
    // false = 카카오가 준 값 || 알 수 없음
    // true = 우리 서비스가 추정한 값
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean travelTimeEstimated = false;

    @Builder
    public CourseSpot(Course course, Long spotId, Integer dayNumber, Integer sequenceOrder, String memo, Integer travelTimeMinutes) {
        this.course = course;
        this.spotId = spotId;
        this.dayNumber = dayNumber;
        this.sequenceOrder = sequenceOrder;
        this.memo = memo;
        this.travelTimeMinutes = travelTimeMinutes;
    }

    public void updateOrder(Integer sequenceOrder) {
        this.sequenceOrder = sequenceOrder;
    }

    public void updateOrderAndMemo(Integer sequenceOrder, String memo) {
        this.sequenceOrder = sequenceOrder;
        this.memo = memo;
    }

    public void updateDayNumberOrderAndMemo(Integer dayNumber, Integer sequenceOrder, String memo) {
        this.dayNumber = dayNumber;
        this.sequenceOrder = sequenceOrder;
        this.memo = memo;
    }

    public void updateTravelTime(Integer travelTimeMinutes, boolean travelTimeEstimated) {
        this.travelTimeMinutes = travelTimeMinutes;
        this.travelTimeEstimated = travelTimeEstimated;
    }
}



