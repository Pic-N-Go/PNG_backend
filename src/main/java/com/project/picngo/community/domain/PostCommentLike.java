package com.project.picngo.community.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 댓글 좋아요. 게시글 좋아요(PostLike)와 같은 구조다 - 유니크 제약으로 중복 좋아요를 DB에서 막고,
 * 카운트는 PostComment.likeCount에 비정규화해 둔다.
 */
@Entity
@Table(name = "community_post_comment_likes", uniqueConstraints =
        @UniqueConstraint(name = "uk_community_post_comment_like", columnNames = {"comment_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostCommentLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private PostComment comment;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public PostCommentLike(PostComment comment, Long userId) {
        this.comment = comment;
        this.userId = userId;
    }
}
