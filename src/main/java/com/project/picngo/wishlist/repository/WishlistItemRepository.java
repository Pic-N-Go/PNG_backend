package com.project.picngo.wishlist.repository;

import com.project.picngo.wishlist.domain.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    List<WishlistItem> findAllByWishlist_UserIdAndIsActiveTrue(Long userId);
}
