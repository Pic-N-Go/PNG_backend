package com.project.picngo.spot.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.spot.domain.enums.TimePeriod;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// user_id는 FK가 아니라 자동 인덱스가 없다. 내 리뷰 목록 조회가 풀스캔이 되므로 직접 걸어준다.
@Table(indexes = @Index(name = "idx_review_user_id", columnList = "user_id"))
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TimePeriod timePeriod;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewPhoto> photos = new ArrayList<>();

    @Comment("방문 날짜. 사용자 직접 입력, nullable")
    @Column
    private LocalDate visitedAt;

    @Builder
    public Review(Spot spot, Long userId, Integer rating, String content, String equipmentInfo, TimePeriod timePeriod, LocalDate visitedAt) {
        this.spot = spot;
        this.userId = userId;
        this.rating = rating;
        this.content = content;
        this.equipmentInfo = equipmentInfo;
        this.timePeriod = timePeriod;
        this.visitedAt = visitedAt;
    }

    public void update(Integer rating, String content, String equipmentInfo, TimePeriod timePeriod, LocalDate visitedAt) {
        this.rating = rating;
        this.content = content;
        this.equipmentInfo = equipmentInfo;
        this.timePeriod = timePeriod;
        this.visitedAt = visitedAt;
    }
}
