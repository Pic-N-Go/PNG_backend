package com.project.picngo.wishlist.repository;

import com.project.picngo.wishlist.domain.Wishlist;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    @EntityGraph(attributePaths = {"weatherConditions", "timeConditions"})
    List<Wishlist> findAllByUserId(Long userId);
    
    Optional<Wishlist> findByUserIdAndSpotId(Long userId, Long spotId);
    
    @EntityGraph(attributePaths = {"weatherConditions", "timeConditions"})
    List<Wishlist> findAllByUserIdAndIsActiveTrue(Long userId);
    
    List<Wishlist> findAllByIsActiveTrue();
}
