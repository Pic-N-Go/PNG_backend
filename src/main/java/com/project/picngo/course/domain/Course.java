package com.project.picngo.course.domain;

import com.project.picngo.common.domain.BaseTimeEntity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    private LocalDate startDate;

    private LocalDate endDate;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseSpot> courseSpots = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseChecklist> courseChecklists = new ArrayList<>();

    @Builder
    public Course(Long userId, String title, LocalDate startDate, LocalDate endDate) {
        this.userId = userId;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void update(String title, LocalDate startDate, LocalDate endDate) {
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * 대한민국 시각(KST) 기준 오늘 날짜 이전에 종료된 코스인지 여부 반환 (완료된 코스: true)
     */
    public boolean isCompleted() {
        if (this.endDate == null) return false;
        return this.endDate.isBefore(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")));
    }
}



