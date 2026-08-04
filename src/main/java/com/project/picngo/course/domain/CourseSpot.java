package com.project.picngo.course.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    // 조회 시점에는 DB 값만 보므로 저장할 때 출처를 같이 남겨야 구분할 수 있다.
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



