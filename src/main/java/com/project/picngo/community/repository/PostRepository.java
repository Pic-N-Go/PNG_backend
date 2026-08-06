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
