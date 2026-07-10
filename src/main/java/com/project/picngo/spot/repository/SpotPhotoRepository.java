package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.SpotPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotPhotoRepository extends JpaRepository<SpotPhoto, Long> {

    long countBySpotId(Long spotId);
}
