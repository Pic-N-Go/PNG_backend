package com.project.picngo.spot.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.spot.domain.enums.TimeSlot;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("리뷰 고유 ID")
    private Long id;

    @Comment("스팟 FK")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private Spot spot;

    @Comment("작성자 유저 ID")
    @Column(nullable = false)
    private Long userId;

    @Comment("별점. 1~5")
    @Column(nullable = false)
    private Integer rating;

    @Comment("리뷰 본문")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Comment("촬영 기기 정보. 예: Sony A7IV + 35mm f1.8")
    @Column(length = 100)
    private String equipmentInfo;

    @Comment("방문 날짜. 사용자 직접 입력, nullable")
    @Column
    private LocalDate visitedAt;

    @Comment("촬영 시간대. SUNRISE/DAY/SUNSET/NIGHT")
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TimeSlot timeSlot;

    @Builder
    public Review(Spot spot, Long userId, Integer rating, String content, String equipmentInfo, LocalDate visitedAt, TimeSlot timeSlot) {
        this.spot = spot;
        this.userId = userId;
        this.rating = rating;
        this.content = content;
        this.equipmentInfo = equipmentInfo;
        this.visitedAt = visitedAt;
        this.timeSlot = timeSlot;
    }

    public void update(Integer rating, String content, String equipmentInfo, LocalDate visitedAt, TimeSlot timeSlot) {
        this.rating = rating;
        this.content = content;
        this.equipmentInfo = equipmentInfo;
        this.visitedAt = visitedAt;
        this.timeSlot = timeSlot;
    }
}
