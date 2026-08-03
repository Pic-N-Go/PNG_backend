package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.SpotAccessPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpotAccessPointRepository extends JpaRepository<SpotAccessPoint, Long> {
    Optional<SpotAccessPoint> findBySpotIdAndIsPrimaryTrue(Long spotId);
    List<SpotAccessPoint> findAllBySpotId(Long spotId);
}
