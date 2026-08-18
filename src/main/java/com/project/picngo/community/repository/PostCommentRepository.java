package com.project.picngo.community.repository;

import com.project.picngo.community.domain.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {
    /** 댓글 목록은 최상위만 준다. 답글은 findByParentId로 따로 조회한다. */
    @EntityGraph(attributePaths = "author")
    Page<PostComment> findByPostIdAndParentIsNull(Long postId, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Page<PostComment> findByParentId(Long parentId, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Optional<PostComment> findByIdAndPostId(Long commentId, Long postId);

    /** 부모 댓글을 지울 때 답글도 함께 지운다. 몇 개를 지웠는지가 게시글 댓글 수 차감에 필요하다. */
    @Modifying
    @Query("""
        delete from PostComment comment
        where comment.parent.id = :parentId
        """)
    int deleteAllByParentId(@Param("parentId") Long parentId);

    @Modifying
    @Query("""
        delete from PostComment comment
        where comment.post.id = :postId
        """)
    void deleteAllByPostId(@Param("postId") Long postId);

    /**
     * 카운트는 엔티티 필드를 직접 고치지 않고 UPDATE로 증감한다 - 동시에 좋아요가 들어와도
     * 마지막 쓰기가 이전 값을 덮어쓰지 않는다(PostRepository.changeLikeCount와 같은 방식).
     *
     * clearAutomatically가 없으면 이 UPDATE가 영속성 컨텍스트를 우회해, 같은 트랜잭션에서
     * 곧바로 다시 읽을 때 1차 캐시의 갱신 전 값이 나온다(응답 카운트가 한 박자 늦는다).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PostComment comment
        set comment.likeCount = comment.likeCount + :delta
        where comment.id = :commentId
        """)
    void changeLikeCount(@Param("commentId") Long commentId, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PostComment comment
        set comment.replyCount = comment.replyCount + :delta
        where comment.id = :commentId
        """)
    void changeReplyCount(@Param("commentId") Long commentId, @Param("delta") int delta);
}
