package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.PhotoAward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PhotoAwardRepository extends JpaRepository<PhotoAward, Long> {

    Optional<PhotoAward> findByContentId(String contentId);

    boolean existsByContentId(String contentId);

    List<PhotoAward> findBySpotId(Long spotId);

    @Query("SELECT p.contentId FROM PhotoAward p WHERE p.contentId IN :contentIds")
    Set<String> findExistingContentIds(@Param("contentIds") Collection<String> contentIds);

    @Query("SELECT p FROM PhotoAward p WHERE p.spot.id = :spotId ORDER BY p.createdAt DESC")
    List<PhotoAward> findBySpotIdOrderByCreatedAtDesc(@Param("spotId") Long spotId);
}
