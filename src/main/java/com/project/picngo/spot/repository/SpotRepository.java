package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotCategory;
import com.project.picngo.spot.domain.SpotStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpotRepository extends JpaRepository<Spot, Long> {

    Page<Spot> findAllByStatusAndIsActiveTrue(
            SpotStatus status,
            Pageable pageable
    );

    Page<Spot> findAllByCategoryAndStatusAndIsActiveTrue(
            SpotCategory category,
            SpotStatus status,
            Pageable pageable
    );

    // 키워드로 스팟 이름, 주소, 설명 검색
    // 카테고리가 null이면 전체 카테고리 검색
    @Query("""
select s
from Spot s
where s.status = :status
and s.isActive = true
and (:category is null or s.category = :category)
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
and (:category is null or s.category = :category)
and s.latitude between :southWestLat and :northEastLat
and s.longitude between :southWestLng and :northEastLng
order by s.photogenicScore decsc, s.bookmarkCount desc
""")
    List<Spot> findSpotInMapBounds(
            @Param("southWestLat") Double southWestLat,
            @Param("southWestLng") Double southWestLng,
            @Param("northEastLat") Double northEastLat,
            @Param("northEastLng") Double northEastLng,
            @Param("category") SpotCategory category,
            @Param("status") SpotStatus status
    );
}
