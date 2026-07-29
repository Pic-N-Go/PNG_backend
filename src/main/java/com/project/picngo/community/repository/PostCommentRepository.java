package com.project.picngo.community.repository;

import com.project.picngo.community.domain.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {
    @EntityGraph(attributePaths = "author")
    Page<PostComment> findByPostId(Long postId, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Optional<PostComment> findByIdAndPostId(Long commentId, Long postId);

    @Modifying
    @Query("""
        delete from PostComment comment
        where comment.post.id = :postId
        """)
    void deleteAllByPostId(@Param("postId") Long postId);
}
