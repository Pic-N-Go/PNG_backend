package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    long countBySpotId(Long spotId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.spot.id = :spotId")
    Double findAvgRatingBySpotId(@Param("spotId") Long spotId);

    Page<Review> findBySpotId(Long spotId, Pageable pageable);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.spot.id = :spotId GROUP BY r.rating")
    List<Object[]> findRatingDistributionBySpotId(@Param("spotId") Long spotId);
}
