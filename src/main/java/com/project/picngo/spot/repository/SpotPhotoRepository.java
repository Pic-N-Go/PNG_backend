package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpotPhotoRepository extends JpaRepository<SpotPhoto, Long> {

    long countBySpotId(Long spotId);

    void deleteBySpotIdAndUserIdIsNull(Long spotId);

    void deleteBySpotIn(List<Spot> spots);

    // TourAPI 대표 사진(userId=null)만, 등록 순서대로
    List<SpotPhoto> findBySpotIdAndUserIdIsNullOrderByIdAsc(Long spotId);
}
