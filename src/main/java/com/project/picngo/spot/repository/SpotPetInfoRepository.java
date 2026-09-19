package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.SpotPetInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface SpotPetInfoRepository extends JpaRepository<SpotPetInfo, Long> {
    Optional<SpotPetInfo> findBySpotId(Long spotId);

    @Query("select info.spot.id from SpotPetInfo info where info.spot.id in :spotIds")
    Set<Long> findExistingSpotIds(@Param("spotIds") Collection<Long> spotIds);
}
