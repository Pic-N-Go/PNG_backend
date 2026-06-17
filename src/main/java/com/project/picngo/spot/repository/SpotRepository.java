package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpotRepository extends JpaRepository<Spot, Long> {

    Page<Spot> findAllByIsActiveTrue(Pageable pageable);

    Page<Spot> findAllByCategoryAndIsActiveTrue(SpotCategory category, Pageable pageable);

    Optional<Spot> findByIdAndIsActiveTrue(Long id);

    @Query("""
            select s
            from Spot s
            where s.isActive = true
              and (:category is null or s.category = :category)
              and (
                  lower(s.name) like lower(concat('%', :keyword, '%'))
                  or lower(s.address) like lower(concat('%', :keyword, '%'))
                  or lower(coalesce(s.description, '')) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<Spot> search(
            @Param("keyword") String keyword,
            @Param("category") SpotCategory category,
            Pageable pageable
    );

    @Query("""
            select s
            from Spot s
            where s.isActive = true
              and (:category is null or s.category = :category)
              and s.latitude between :southWestLat and :northEastLat
              and s.longitude between :southWestLng and :northEastLng
            order by s.photogenicScore desc, s.bookmarkCount desc
            """)
    List<Spot> findSpotsInMapBounds(
            @Param("southWestLat") Double southWestLat,
            @Param("southWestLng") Double southWestLng,
            @Param("northEastLat") Double northEastLat,
            @Param("northEastLng") Double northEastLng,
            @Param("category") SpotCategory category
    );
}
