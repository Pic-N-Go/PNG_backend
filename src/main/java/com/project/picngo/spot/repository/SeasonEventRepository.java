package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.SeasonEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeasonEventRepository extends JpaRepository<SeasonEvent, Long> {

    @Query("SELECT s FROM SeasonEvent s WHERE s.isActive = true AND (s.region IS NULL OR s.region = :region)")
    List<SeasonEvent> findActiveByRegion(@Param("region") String region);
}
