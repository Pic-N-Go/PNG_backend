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

    @Query(value = """
            SELECT * FROM spot
            WHERE is_active = true
            ORDER BY (review_count + bookmark_count) DESC, RAND()
            LIMIT :limit
            """, nativeQuery = true)
    List<Spot> findRecommendedSpots(@Param("limit") int limit);

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
