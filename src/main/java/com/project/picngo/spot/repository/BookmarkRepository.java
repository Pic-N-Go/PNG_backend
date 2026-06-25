package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsBySpotIdAndUserId(Long spotId, Long userId);

    Optional<Bookmark> findBySpotIdAndUserId(Long spotId, Long userId);
}
