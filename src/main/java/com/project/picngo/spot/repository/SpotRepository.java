package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Spot;
import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.domain.enums.SpotStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpotRepository extends JpaRepository<Spot, Long> {

    Optional<Spot> findByTourContentId(String tourContentId);

    @Query(value = """
            SELECT *, (6371 * acos(cos(radians(:lat)) * cos(radians(latitude))
                * cos(radians(longitude) - radians(:lng)) + sin(radians(:lat)) * sin(radians(latitude)))) AS distance
            FROM spot
            WHERE is_active = true
            HAVING distance < :radiusKm
            ORDER BY distance
            LIMIT :limit
            """, nativeQuery = true)
    List<Spot> findNearbySpots(@Param("lat") Double lat, @Param("lng") Double lng,
                               @Param("radiusKm") Double radiusKm, @Param("limit") int limit);

    // 유저 관심테마와 겹치는 스팟을 앞으로, 나머지는 기존 인기순.
    // 관심테마가 없는 유저(소셜 가입 등)는 EXISTS가 전부 0이라 인기순으로 자연 폴백된다.
    // ponytail: 행마다 EXISTS + 전체 정렬. 스팟 수천 건 규모에선 충분, 수십만 건 되면 매칭 스팟만 먼저 뽑아 합치는 방식으로 교체
    @Query(value = """
            SELECT s.* FROM spot s
            WHERE s.is_active = true
            ORDER BY EXISTS (
                SELECT 1 FROM spot_categories sc
                JOIN user_spot_categories uc ON uc.category = sc.category
                WHERE sc.spot_id = s.id AND uc.user_id = :userId
            ) DESC,
            (s.review_count + s.bookmark_count) DESC, RAND()
            LIMIT :limit
            """, nativeQuery = true)
    List<Spot> findRecommendedSpots(@Param("userId") Long userId, @Param("limit") int limit);

    Page<Spot> findAllByStatusAndIsActiveTrue(
            SpotStatus status,
            Pageable pageable
    );

    // categories(Set)에 :category가 포함된 스팟. 파생 메서드로 표현 불가 → member of
    @Query("""
select s from Spot s
where :category member of s.categories
and s.status = :status and s.isActive = true
""")
    Page<Spot> findAllByCategoryAndStatusAndIsActiveTrue(
            @Param("category") SpotCategory category,
            @Param("status") SpotStatus status,
            Pageable pageable
    );

    List<Spot> findListByStatusAndIsActiveTrue(
            SpotStatus status,
            Pageable pageable
    );

    @Query("""
select s from Spot s
where :category member of s.categories
and s.status = :status and s.isActive = true
""")
    List<Spot> findListByCategoryAndStatusAndIsActiveTrue(
            @Param("category") SpotCategory category,
            @Param("status") SpotStatus status,
            Pageable pageable
    );

    // 키워드로 스팟 이름, 주소, 설명 검색
    // 카테고리가 null이면 전체 카테고리 검색
    @Query("""
select s
from Spot s
where s.status = :status
and s.isActive = true
and (:category is null or :category member of s.categories)
and (
lower(s.name) like lower(concat('%', :keyword, '%'))
or lower(s.address) like lower(concat('%', :keyword, '%'))
or lower(coalesce(s.overview, '')) like lower(concat('%', :keyword, '%'))
)
""")
    Page<Spot> searchSpots(
            @Param("keyword") String keyword,
            @Param("category") SpotCategory category,
            @Param("status") SpotStatus status,
            Pageable pageable
    );

    // 지도 화면의 현재 영역 안에 있는 스팟 조회
    @Query("""
select s
from Spot s
where s.status = :status
and s.isActive = true
and (:category is null or :category member of s.categories)
and s.latitude between :southWestLat and :northEastLat
and s.longitude between :southWestLng and :northEastLng
order by s.photogenicScore desc, s.bookmarkCount desc
""")
    List<Spot> findSpotsInMapBounds(
            @Param("southWestLat") Double southWestLat,
            @Param("southWestLng") Double southWestLng,
            @Param("northEastLat") Double northEastLat,
            @Param("northEastLng") Double northEastLng,
            @Param("category") SpotCategory category,
            @Param("status") SpotStatus status,
            Pageable pageable
    );

    Optional<Spot> findByIdAndStatusAndIsActiveTrue(
            Long id,
            SpotStatus status
    );

    List<Spot> findByIdIn(List<Long> ids);
}
