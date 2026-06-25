package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.domain.Bookmark;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.dto.BookmarkResponse;
import com.project.picngo.spot.repository.BookmarkRepository;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    // ponytail: Spring Security 연동 전까지 하드코딩
    private static final Long TEMP_USER_ID = 1L;

    private final BookmarkRepository bookmarkRepository;
    private final SpotRepository spotRepository;

    @Transactional
    public BookmarkResponse toggle(Long spotId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        return bookmarkRepository.findBySpotIdAndUserId(spotId, TEMP_USER_ID)
                .map(bookmark -> {
                    bookmarkRepository.delete(bookmark);
                    return new BookmarkResponse(false);
                })
                .orElseGet(() -> {
                    bookmarkRepository.save(Bookmark.builder().spot(spot).userId(TEMP_USER_ID).build());
                    return new BookmarkResponse(true);
                });
    }
}
