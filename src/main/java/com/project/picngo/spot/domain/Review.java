package com.project.picngo.spot.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.spot.domain.enums.ReviewTag;
import com.project.picngo.spot.domain.enums.TimePeriod;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// user_id는 FK가 아니라 자동 인덱스가 없다. 내 리뷰 목록 조회가 풀스캔이 되므로 직접 걸어준다.
// 스팟당 1인 1리뷰는 앱 레벨 검사만으로는 동시 요청을 막지 못해 DB 제약을 함께 둔다.
@Table(
        indexes = @Index(name = "idx_review_user_id", columnList = "user_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_review_spot_user", columnNames = {"spot_id", "user_id"})
)
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

    @Comment("리뷰 태그. 고정 9종 중 최대 5개")
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "review_tag", joinColumns = @JoinColumn(name = "review_id"))
    @Column(name = "tag", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Set<ReviewTag> tags = new LinkedHashSet<>();

    @Comment("방문 날짜. 사용자 직접 입력, nullable")
    @Column
    private LocalDate visitedAt;

    @Builder
    public Review(Spot spot, Long userId, Integer rating, String content, String equipmentInfo, TimePeriod timePeriod, LocalDate visitedAt, Set<ReviewTag> tags) {
        this.spot = spot;
        this.userId = userId;
        this.rating = rating;
        this.content = content;
        this.equipmentInfo = equipmentInfo;
        this.timePeriod = timePeriod;
        this.visitedAt = visitedAt;
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

    public void update(Integer rating, String content, String equipmentInfo, TimePeriod timePeriod, LocalDate visitedAt, Set<ReviewTag> tags) {
        this.rating = rating;
        this.content = content;
        this.equipmentInfo = equipmentInfo;
        this.timePeriod = timePeriod;
        this.visitedAt = visitedAt;
        // 컬렉션은 새 인스턴스로 바꾸면 orphan 관리가 깨진다. 내용만 교체한다.
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }
}
