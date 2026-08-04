package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.SpotAccessPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpotAccessPointRepository extends JpaRepository<SpotAccessPoint, Long> {
    List<SpotAccessPoint> findAllBySpotId(Long spotId);
}
