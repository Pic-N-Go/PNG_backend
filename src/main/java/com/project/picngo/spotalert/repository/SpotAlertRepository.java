package com.project.picngo.spotalert.repository;

import com.project.picngo.spotalert.domain.SpotAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SpotAlertRepository extends JpaRepository<SpotAlert, Long> {
    List<SpotAlert> findAllByUserId(Long userId);
    
    Optional<SpotAlert> findByUserIdAndSpotId(Long userId, Long spotId);
    
    List<SpotAlert> findAllByUserIdAndIsActiveTrue(Long userId);
    
    List<SpotAlert> findAllByUserIdInAndIsActiveTrue(List<Long> userIds);
    
    List<SpotAlert> findAllByIsActiveTrue();
}
