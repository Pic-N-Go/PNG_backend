package com.project.picngo.community.repository;

import com.project.picngo.community.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id = :postId")
    java.util.Optional<Post> findByIdForUpdate(@Param("postId") Long postId);

    @EntityGraph(attributePaths = {"author", "spot"})
    @Query("""
            select p from Post p
            left join p.spot spot
            where (:keyword is null
                or lower(p.content) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(spot.name, '')) like lower(concat('%', :keyword, '%')))
              and (:authorId is null or p.author.id = :authorId)
            """)

    Page<Post> search(
            @Param("keyword") String keyword,
            @Param("authorId") Long authorId,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount + :delta where p.id = :postId")
    void changeLikeCount(@Param("postId") Long postId, @Param("delta") long delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.bookmarkCount = p.bookmarkCount + :delta where p.id = :postId")
    void changeBookmarkCount(@Param("postId") Long postId, @Param("delta") long delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :postId")
    void incrementCommentCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Post p
            set p.commentCount =
                case when p.commentCount > 0 then p.commentCount - 1 else 0 end
            where p.id = :postId
            """)
    void decrementCommentCount(@Param("postId") Long postId);

    /**
     * 댓글 수를 임의 폭으로 줄인다. 답글이 달린 댓글을 지우면 한 번에 여러 개가 사라져
     * decrementCommentCount(-1 고정)로는 맞출 수 없다. 음수로 떨어지지 않게 0에서 멈춘다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Post p
            set p.commentCount =
                case when p.commentCount + :delta > 0 then p.commentCount + :delta else 0 end
            where p.id = :postId
            """)
    void changeCommentCount(@Param("postId") Long postId, @Param("delta") long delta);

    // 내가 저장한 글. 정렬은 저장 시각이 아니라 작성 시각이다 —
    // PostBookmark에 생성 시각 컬럼이 없고, 다른 목록과 같은 기준이라 페이지를 넘겨도 순서가 안 흔들린다.
    @EntityGraph(attributePaths = {"author", "spot"})
    @Query("""
        select p
        from Post p
        left join p.spot spot
        where exists (
            select bookmark.id
            from PostBookmark bookmark
            where bookmark.userId = :userId
              and bookmark.post.id = p.id
        )
        and (
            :keyword is null
            or lower(p.content) like lower(concat('%', :keyword, '%'))
            or lower(coalesce(spot.name, '')) like lower(concat('%', :keyword, '%'))
        )
        """)
    Page<Post> searchBookmarked(
            @Param("keyword") String keyword,
            @Param("userId") Long userId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"author", "spot"})
    @Query("""
        select p
        from Post p
        left join p.spot spot
        where p.author.id <> :userId and exists (
            select f.id
            from Follow f
            where f.follower.id = :userId
              and f.following.id = p.author.id
        )
        and (
            :keyword is null
            or lower(p.content) like lower(concat('%', :keyword, '%'))
            or lower(coalesce(spot.name, '')) like lower(concat('%', :keyword, '%'))
        )
        """)
    Page<Post> searchFollowing(
            @Param("keyword") String keyword,
            @Param("userId") Long userId,
            Pageable pageable
    );
}
