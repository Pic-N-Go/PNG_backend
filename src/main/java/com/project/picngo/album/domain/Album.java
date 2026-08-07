package com.project.picngo.album.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "albums")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Album extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 앨범을 생성한 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 앨범 이름
    @Column(nullable = false, length = 100)
    private String name;

    // 앨범 카테고리 태그
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SpotCategory category;

    // 공개 여부
    @Column(nullable = false)
    private boolean isPublic;

    private Album(User user, String name, SpotCategory category, boolean isPublic) {
        this.user = user;
        this.name = name;
        this.category = category;
        this.isPublic = isPublic;
    }

    // 앨범 생성
    public static Album create(User user, String name, SpotCategory category, boolean isPublic) {
        return new Album(user, name, category, isPublic);
    }

    // 앨범 정보 수정
    public void update(String name, SpotCategory category, boolean isPublic) {
        this.name = name;
        this.category = category;
        this.isPublic = isPublic;
    }
}
