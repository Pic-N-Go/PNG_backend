package com.project.picngo.wishlist.service;

import com.project.picngo.external.WeatherClient;
import com.project.picngo.wishlist.domain.Wishlist;
import com.project.picngo.wishlist.dto.*;
import com.project.picngo.wishlist.repository.WishlistRepository;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.WishlistErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.user.repository.UserRepository;
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
    private final WeatherClient weatherClient;
    private final UserRepository userRepository;

    public List<WishlistSettingResponse> getWishlists(Long userId) {
        validateUserExists(userId);
        return wishlistRepository.findAllByUserId(userId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public WishlistSettingResponse getWishlistDetail(Long userId, Long spotId) {
        validateUserExists(userId);
        Wishlist wishlist = wishlistRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseThrow(() -> new CustomException(WishlistErrorCode.WISHLIST_NOT_FOUND_OR_UNAUTHORIZED));
        return convertToResponse(wishlist);
    }

    @Transactional
    public WishlistSettingResponse updateWishlistSettings(Long userId, Long spotId, WishlistSettingUpdateRequest request) {
        validateUserExists(userId);
        
        Wishlist wishlist = wishlistRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseGet(() -> Wishlist.builder()
                        .userId(userId)
                        .spotId(spotId)
                        .build());
        
        wishlist.updateSettings(
                request.memo(),
                request.weatherConditions(),
                request.timeConditions(),
                request.alertTimingDays(),
                request.isAlertEnabled()
        );

        Wishlist saved = wishlistRepository.save(wishlist);
        return convertToResponse(saved);
    }

    @Transactional
    public void deleteWishlist(Long userId, Long spotId) {
        validateUserExists(userId);
        Wishlist wishlist = wishlistRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseThrow(() -> new CustomException(WishlistErrorCode.WISHLIST_NOT_FOUND_OR_UNAUTHORIZED));
        wishlistRepository.delete(wishlist);
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    private WishlistSettingResponse convertToResponse(Wishlist wishlist) {
        // TODO: 향후 Spot 엔티티 연동하여 spotName, address, tags 등 채우기
        // TODO: 기상청 중기예보(7일) API 연동 및 DND 시간(NotificationSetting) 조회 로직 추가
        return new WishlistSettingResponse(
                wishlist.getSpotId(),
                "스팟 이름 (임시)", 
                "주소 (임시)",
                0,
                List.of(),
                wishlist.getMemo(),
                wishlist.getWeatherConditions(),
                wishlist.getTimeConditions(),
                wishlist.getIsActive(),
                wishlist.getAlertTimingDays(),
                null,
                null,
                List.of()
        );
    }
}
