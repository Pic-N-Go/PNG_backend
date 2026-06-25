package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.SpotTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpotTagRepository extends JpaRepository<SpotTag, Long> {

    List<SpotTag> findBySpotId(Long spotId);
}
