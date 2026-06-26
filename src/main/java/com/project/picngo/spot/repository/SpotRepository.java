package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Spot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotRepository extends JpaRepository<Spot, Long> {
    boolean existsByTourContentId(String tourContentId);

}
