package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpotRepository extends JpaRepository<Spot, Long> {

    Page<Spot> findAllByIsActiveTrue(Pageable pageable);

    Page<Spot> findAllByCategoryAndIsActiveTrue(SpotCategory category, Pageable pageable);

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
}
