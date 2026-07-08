package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotCategory;
import com.project.picngo.spot.domain.SpotStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotRepository extends JpaRepository<Spot, Long> {

    Page<Spot> findAllByStatusAndIsActiveTrue(
            SpotStatus status,
            Pageable pageable
    );

    Page<Spot> findAllByCategoryAndStatusAndIsActiveTrue(
            SpotCategory category,
            SpotStatus status,
            Pageable pageable
    );
}
