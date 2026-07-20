package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.HiddenChecklistDefault;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HiddenChecklistDefaultRepository extends JpaRepository<HiddenChecklistDefault, Long> {

    List<HiddenChecklistDefault> findBySpotIdAndUserId(Long spotId, Long userId);
    boolean existsBySpotIdAndUserIdAndDefaultItemId(Long spotId, Long userId, Integer defaultItemId);
    void deleteBySpotIdAndUserIdAndDefaultItemId(Long spotId, Long userId, Integer defaultItemId);
}
