package com.project.picngo.community.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "community_post_comments",
        indexes = {
                // 목록은 "부모가 없는 댓글"과 "특정 부모의 답글"만 조회한다 - 두 경로 모두 이 인덱스를 탄다.
                @Index(name = "idx_post_comment_parent", columnList = "post_id, parent_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostComment extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    /**
     * 답글이면 원 댓글, 최상위 댓글이면 null. 인스타그램과 같이 깊이는 1단계까지만 둔다 -
     * 답글에 답글을 달아도 서비스가 같은 부모로 붙이므로 이 필드가 답글을 가리키는 일은 없다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private PostComment parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 500)
    private String content;

    /** 목록마다 세면 N+1이 되므로 게시글(Post)과 같이 비정규화해 들고 있는다. */
    @Column(nullable = false)
    private int likeCount;

    /** 최상위 댓글에만 쌓인다. 답글은 항상 0이다. */
    @Column(nullable = false)
    private int replyCount;

    public PostComment(Post post, PostComment parent, User author, String content) {
        this.post = post;
        this.parent = parent;
        this.author = author;
        this.content = content;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public boolean isReply() {
        return parent != null;
    }
}
