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

    /**
     * 낙관적 락용 버전. 저장할 때마다 JPA가 1씩 올리고,
     * {@code UPDATE ... WHERE id = ? AND version = ?} 조건으로 충돌을 잡아낸다.
     *
     * <p>주의: 이 값이 <b>언제 오르는지</b>가 이 락의 유효 범위를 결정한다.
     * 자식(CourseSpot)의 필드만 바뀌는 경우에도 오르는지는 실측으로 확인해야 한다
     * (CourseVersionBehaviorTest). 안 오른다면 순서 변경은 이 락으로 보호되지 않는다.
     */
    @Version
    private Long version;

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



