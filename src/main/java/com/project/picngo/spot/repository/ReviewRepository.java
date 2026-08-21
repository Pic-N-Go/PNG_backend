package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.dto.ReviewedSpotResponse;
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

    // PIC MAP의 리뷰 핀. 지도는 전체 핀이 필요해 페이징이 없고, 본문·태그·사진을 쓰지 않아
    // 엔티티 대신 DTO projection으로 필요한 컬럼만 읽는다 (presigned URL 서명 비용도 안 든다).
    // ponytail: isActive·status로는 걸러내지 않는다. 내가 리뷰를 남긴 기록을 말없이 감추면
    // 마이페이지 리뷰 목록과 지도 핀 개수가 어긋난다 (getBookmarkedSpots도 같은 이유로 안 건다).
    @Query("SELECT new com.project.picngo.spot.dto.ReviewedSpotResponse("
            + "s.id, s.name, s.address, s.latitude, s.longitude, "
            + "COALESCE(NULLIF(TRIM(s.thumbnailUrl), ''), NULLIF(TRIM(s.imageUrl), '')), "
            + "r.createdAt, r.rating) "
            + "FROM Review r JOIN r.spot s "
            + "WHERE r.userId = :userId "
            // 작성일이 같은 리뷰가 생긴다(연속 저장). 타이브레이커가 없으면 요청마다 핀 순서가 흔들린다.
            + "ORDER BY r.createdAt DESC, r.id DESC")
    List<ReviewedSpotResponse> findReviewedSpotsByUserId(@Param("userId") Long userId);

    // TRIM을 씌우는 이유: /me/reviews는 MyReviewInfo.firstNonBlank가 isBlank()로 걸러서
    // 공백만 있는 thumbnailUrl도 폴백시킨다. NULLIF(x, '')만으로는 "  "가 그대로 내려가
    // 두 엔드포인트가 같은 스팟을 다르게 표현한다(프론트 폴백은 null 기준이라 깨진 이미지가 뜬다).
    //
    // ponytail: SQL TRIM은 공백만 걷어낸다 — 탭·개행만 담긴 값은 여기서 걸러지지 않아
    // isBlank()와 완전히 일치하지 않는다. 실제 문제는 TourAPI가 주는 빈 문자열이고 그건
    // 잡힌다. 탭·개행까지 맞춰야 하면 SQL로는 안 되고, 두 컬럼을 그대로 내려 서비스에서
    // firstNonBlank를 재사용해야 한다(projection이 10인자가 되고 조립 단계가 하나 붙는다).

    // 위 projection은 컬렉션을 실을 수 없어 카테고리만 따로 일괄 조회한다.
    // 엔티티를 읽어 spot.getCategoryNames()를 부르는 방법도 있지만, Spot.embedding이
    // MEDIUMBLOB이고 lazy가 아니라 스팟마다 임베딩 벡터까지 끌고 온다.
    @Query("SELECT r.spot.id, c FROM Review r JOIN r.spot.categories c WHERE r.userId = :userId")
    List<Object[]> findReviewedSpotCategories(@Param("userId") Long userId);

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
