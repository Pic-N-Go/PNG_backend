package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Spot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SpotRepository extends JpaRepository<Spot, Long> {
    boolean existsByTourContentId(String tourContentId);

}
