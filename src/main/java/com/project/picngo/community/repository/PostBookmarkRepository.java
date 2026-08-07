package com.project.picngo.community.repository;

import com.project.picngo.community.domain.PostBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostBookmarkRepository extends JpaRepository<PostBookmark, Long> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);

    Optional<PostBookmark> findByPostIdAndUserId(Long postId, Long userId);

    @Query("""
            select bookmark.post.id
            from PostBookmark bookmark
            where bookmark.userId = :userId
              and bookmark.post.id in :postIds
            """)
    List<Long> findBookmarkedPostIds(
            @Param("userId") Long userId,
            @Param("postIds") List<Long> postIds
    );

    @Modifying
    @Query("""
        delete from PostBookmark bookmark
        where bookmark.post.id = :postId
        """)
    void deleteAllByPostId(@Param("postId") Long postId);
}
