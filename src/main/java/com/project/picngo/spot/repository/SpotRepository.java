package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Spot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpotRepository extends JpaRepository<Spot, Long> {
    boolean existsByTourContentId(String tourContentId);
    Optional<Spot> findByTourContentId(String tourContentId);
}
