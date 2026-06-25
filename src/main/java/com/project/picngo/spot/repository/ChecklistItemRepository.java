package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.ChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    List<ChecklistItem> findBySpotIdOrderByOrderIndex(Long spotId);
}
