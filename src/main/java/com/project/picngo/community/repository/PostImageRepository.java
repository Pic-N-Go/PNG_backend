package com.project.picngo.community.repository;

import com.project.picngo.community.domain.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    List<PostImage> findByPostIdOrderByPostOrderAsc(Long postId);

    @Query("""
            select image.objectKey
            from PostImage image
            where image.post.id = :postId
            order by image.postOrder asc
            """)
    List<String> findObjectKeysByPostId(@Param("postId") Long postId);

    @Query("""
            select image
            from PostImage image
            where image.post.id in :postIds
            order by image.post.id asc, image.postOrder asc
            """)
    List<PostImage> findAllByPostIds(@Param("postIds") List<Long> postIds);

    @Modifying
    @Query("""
            delete from PostImage image
            where image.post.id = :postId
            """)
    void deleteAllByPostId(@Param("postId") Long postId);
}
