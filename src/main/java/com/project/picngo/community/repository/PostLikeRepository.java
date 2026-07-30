package com.project.picngo.community.repository;

import com.project.picngo.community.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    @Query("""
            select postLike.post.id
            from PostLike postLike
            where postLike.userId = :userId
              and postLike.post.id in :postIds
            """)
    List<Long> findLikedPostIds(
            @Param("userId") Long userId,
            @Param("postIds") List<Long> postIds
    );

    @Modifying
    @Query("""
        delete from PostLike postLike
        where postLike.post.id = :postId
        """)
    void deleteAllByPostId(@Param("postId") Long postId);
}
