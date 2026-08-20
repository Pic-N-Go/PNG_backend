package com.project.picngo.community.repository;

import com.project.picngo.community.domain.PostCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostCommentLikeRepository extends JpaRepository<PostCommentLike, Long> {
    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    Optional<PostCommentLike> findByCommentIdAndUserId(Long commentId, Long userId);

    /** 목록 한 페이지의 "내가 누른 댓글"을 한 번에 구한다 - 댓글마다 조회하면 N+1이 된다. */
    @Query("""
            select commentLike.comment.id
            from PostCommentLike commentLike
            where commentLike.userId = :userId
              and commentLike.comment.id in :commentIds
            """)
    List<Long> findLikedCommentIds(
            @Param("userId") Long userId,
            @Param("commentIds") List<Long> commentIds
    );

    @Modifying
    @Query("""
        delete from PostCommentLike commentLike
        where commentLike.comment.id in :commentIds
        """)
    void deleteAllByCommentIdIn(@Param("commentIds") List<Long> commentIds);

    /** 게시글을 지울 때 그 게시글의 모든 댓글 좋아요를 함께 지운다. */
    @Modifying
    @Query("""
        delete from PostCommentLike commentLike
        where commentLike.comment.id in (
            select comment.id from PostComment comment where comment.post.id = :postId
        )
        """)
    void deleteAllByPostId(@Param("postId") Long postId);
}
