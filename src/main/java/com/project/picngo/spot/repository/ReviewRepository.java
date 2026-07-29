package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("SELECT AVG(r.rating), COUNT(r) FROM Review r WHERE r.spot.id = :spotId")
    List<Object[]> findAvgAndCountBySpotId(@Param("spotId") Long spotId);

    Page<Review> findBySpotId(Long spotId, Pageable pageable);

    // 내 리뷰 목록은 스팟명·썸네일이 필요해 spot을 함께 조회한다 (fetch join 없으면 페이지당 N+1)
    @Query(value = "SELECT r FROM Review r JOIN FETCH r.spot WHERE r.userId = :userId",
            countQuery = "SELECT COUNT(r) FROM Review r WHERE r.userId = :userId")
    Page<Review> findByUserIdWithSpot(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.spot.id = :spotId GROUP BY r.rating")
    List<Object[]> findRatingDistributionBySpotId(@Param("spotId") Long spotId);
}
