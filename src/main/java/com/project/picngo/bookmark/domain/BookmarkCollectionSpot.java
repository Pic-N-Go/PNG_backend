package com.project.picngo.bookmark.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

// 컬렉션 ↔ 스팟 다대다 멤버십. userId는 collection에 있으므로 여기 중복 저장하지 않음.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames = {"collection_id", "spot_id"}),
        indexes = @Index(name = "idx_membership_spot", columnList = "spot_id"))
public class BookmarkCollectionSpot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("멤버십 ID")
    private Long id;

    @Comment("컬렉션 FK")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private BookmarkCollection collection;

    @Comment("스팟 ID")
    @Column(nullable = false)
    private Long spotId;

    @Builder
    public BookmarkCollectionSpot(BookmarkCollection collection, Long spotId) {
        this.collection = collection;
        this.spotId = spotId;
    }
}
