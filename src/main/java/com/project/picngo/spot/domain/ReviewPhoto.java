package com.project.picngo.spot.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("리뷰 사진 고유 ID")
    private Long id;

    @Comment("리뷰 FK")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Comment("사진 URL")
    @Column(nullable = false, length = 500)
    private String photoUrl;

    @Builder
    public ReviewPhoto(Review review, String photoUrl) {
        this.review = review;
        this.photoUrl = photoUrl;
    }
}
