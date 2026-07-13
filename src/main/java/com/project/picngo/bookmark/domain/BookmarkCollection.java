package com.project.picngo.bookmark.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "idx_collection_user", columnList = "userId"))
public class BookmarkCollection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("북마크 컬렉션 ID")
    private Long id;

    @Comment("유저 ID")
    @Column(nullable = false)
    private Long userId;

    @Comment("컬렉션 이름 (최대 20자)")
    @Column(nullable = false, length = 20)
    private String name;

    // color/icon: 프론트 소유의 문자열 키만 저장 (색 hex·글리프는 프론트가 매핑). 허용값 검증은 서비스에서.
    @Comment("색상 키 (pink, blue 등)")
    @Column(nullable = false, length = 20)
    private String color;

    @Comment("아이콘 키 (star, heart 등)")
    @Column(nullable = false, length = 20)
    private String icon;

    @Builder
    public BookmarkCollection(Long userId, String name, String color, String icon) {
        this.userId = userId;
        this.name = name;
        this.color = color;
        this.icon = icon;
    }
}
