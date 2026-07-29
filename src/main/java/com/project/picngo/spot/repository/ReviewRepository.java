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

    boolean existsBySpotIdAndUserId(Long spotId, Long userId);

    // 프론트가 작성/수정 분기를 한 번의 조회로 판단하게 한다.
    // 유니크 제약을 걸었지만 수동 마이그레이션이라 적용 전 환경에는 중복 행이 남아 있을 수 있다.
    // Optional로 받으면 그 경우 IncorrectResultSizeDataAccessException → 스팟 상세가 500이 된다.
    @Query("SELECT r.id FROM Review r WHERE r.spot.id = :spotId AND r.userId = :userId ORDER BY r.id ASC")
    List<Long> findIdsBySpotIdAndUserId(@Param("spotId") Long spotId, @Param("userId") Long userId);

    // 내 리뷰 목록은 스팟명·썸네일이 필요해 spot을 함께 조회한다 (fetch join 없으면 페이지당 N+1)
    @Query(value = "SELECT r FROM Review r JOIN FETCH r.spot WHERE r.userId = :userId",
            countQuery = "SELECT COUNT(r) FROM Review r WHERE r.userId = :userId")
    Page<Review> findByUserIdWithSpot(@Param("userId") Long userId, Pageable pageable);

    // "자주 쓰인 태그" — 2회 이상 등장한 것만, 많이 쓰인 순. 상위 N개 절삭은 호출부에서 한다.
    // 리뷰가 적은 스팟에서 1회짜리 태그가 대표 태그로 뜨는 것을 막기 위해 HAVING을 둔다.
    @Query("SELECT t, COUNT(t) FROM Review r JOIN r.tags t WHERE r.spot.id = :spotId "
            + "GROUP BY t HAVING COUNT(t) >= 2 ORDER BY COUNT(t) DESC, t ASC")
    List<Object[]> findFrequentTagsBySpotId(@Param("spotId") Long spotId);

    // 목록 조회용 태그 일괄 로딩. 지연 로딩에 맡기면 리뷰 행마다 review_tag를 한 번씩 조회한다.
    @Query("SELECT r.id, t FROM Review r JOIN r.tags t WHERE r.id IN :reviewIds ORDER BY t ASC")
    List<Object[]> findTagsByReviewIds(@Param("reviewIds") List<Long> reviewIds);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.spot.id = :spotId GROUP BY r.rating")
    List<Object[]> findRatingDistributionBySpotId(@Param("spotId") Long spotId);
}
