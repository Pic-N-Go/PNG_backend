package com.project.picngo.user.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 차단. 차단하면 그 사용자의 게시글·댓글이 피드에서 보이지 않는다.
 *
 * 단방향이다 — A가 B를 차단해도 B 입장에서는 아무 변화가 없고, B에게 알리지도 않는다.
 * 차단당한 사실을 알리는 서비스는 거의 없고, 알리면 보복으로 이어진다.
 */
@Getter
@Entity
@Table(
        name = "user_blocks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_blocker_blocked",
                        columnNames = {"blocker_id", "blocked_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 차단한 사람 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    /** 차단당한 사람 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    private UserBlock(User blocker, User blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
    }

    public static UserBlock create(User blocker, User blocked) {
        return new UserBlock(blocker, blocked);
    }
}
