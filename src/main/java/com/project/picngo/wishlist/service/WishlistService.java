package com.project.picngo.wishlist.service;

import com.project.picngo.external.WeatherClient;
import com.project.picngo.wishlist.domain.Wishlist;
import com.project.picngo.wishlist.domain.WishlistItem;
import com.project.picngo.wishlist.dto.*;
import com.project.picngo.wishlist.repository.WishlistItemRepository;
import com.project.picngo.wishlist.repository.WishlistRepository;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.WishlistErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final WeatherClient weatherClient;

    public List<WishlistResponse> getWishlist(Long userId) {
        // Fetch all wishlists (folders) for the user.
        // For production, use Fetch Join to avoid N+1 problem with items.
        // For now, we rely on lazy loading (which may trigger N+1) until we optimize queries.
        return wishlistRepository.findAllByUserId(userId).stream()
                .map(WishlistResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public WishlistResponse createWishlist(Long userId, WishlistCreateRequest request) {
        Wishlist wishlist = Wishlist.builder()
                .userId(userId)
                .name(request.name())
                .build();
        Wishlist saved = wishlistRepository.save(wishlist);
        return WishlistResponse.from(saved);
    }

    public WishlistResponse getWishlistDetail(Long id, Long userId) {
        Wishlist wishlist = wishlistRepository.findById(id)
                .filter(w -> w.getUserId().equals(userId))
                .orElseThrow(() -> new CustomException(WishlistErrorCode.WISHLIST_NOT_FOUND_OR_UNAUTHORIZED));
        return WishlistResponse.from(wishlist);
    }

    @Transactional
    public WishlistResponse updateWishlist(Long id, Long userId, WishlistUpdateRequest request) {
        Wishlist wishlist = wishlistRepository.findById(id)
                .filter(w -> w.getUserId().equals(userId))
                .orElseThrow(() -> new CustomException(WishlistErrorCode.WISHLIST_NOT_FOUND_OR_UNAUTHORIZED));
        
        wishlist.updateName(request.name());
        return WishlistResponse.from(wishlist);
    }

    @Transactional
    public void deleteWishlist(Long id, Long userId) {
        Wishlist wishlist = wishlistRepository.findById(id)
                .filter(w -> w.getUserId().equals(userId))
                .orElseThrow(() -> new CustomException(WishlistErrorCode.WISHLIST_NOT_FOUND_OR_UNAUTHORIZED));
        wishlistRepository.delete(wishlist);
    }

    @Transactional
    public WishlistItemResponse addItemToWishlist(Long id, Long userId, WishlistItemRequest request) {
        Wishlist wishlist = wishlistRepository.findById(id)
                .filter(w -> w.getUserId().equals(userId))
                .orElseThrow(() -> new CustomException(WishlistErrorCode.WISHLIST_NOT_FOUND_OR_UNAUTHORIZED));

        WishlistItem item = WishlistItem.builder()
                .wishlist(wishlist)
                .spotId(request.spotId())
                .weatherCondition(request.weatherCondition())
                .timeCondition(request.timeCondition())
                .build();
        
        wishlist.addItem(item);
        WishlistItem savedItem = wishlistItemRepository.save(item);
        
        return WishlistItemResponse.from(savedItem);
    }

    @Transactional
    public void removeItemFromWishlist(Long id, Long itemId, Long userId) {
        Wishlist wishlist = wishlistRepository.findById(id)
                .filter(w -> w.getUserId().equals(userId))
                .orElseThrow(() -> new CustomException(WishlistErrorCode.WISHLIST_NOT_FOUND_OR_UNAUTHORIZED));

        WishlistItem item = wishlistItemRepository.findById(itemId)
                .filter(i -> i.getWishlist().getId().equals(wishlist.getId()))
                .orElseThrow(() -> new CustomException(WishlistErrorCode.WISHLIST_ITEM_NOT_FOUND));

        wishlist.getItems().remove(item);
    }

    @Transactional
    public void checkConditionsAndNotify() {
        // TODO: Scheduler logic for later
    }
}
