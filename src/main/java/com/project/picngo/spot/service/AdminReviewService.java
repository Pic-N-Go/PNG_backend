package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ReviewErrorCode;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.ReviewPhotoRepository;
import com.project.picngo.spot.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewPhotoRepository reviewPhotoRepository;
    private final ImageStorageService imageStorageService;

    @Transactional
    public void deleteReviewByAdmin(Long reviewId) {
        Review review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new CustomException(ReviewErrorCode.REVIEW_NOT_FOUND));
        Spot spot = review.getSpot();

        List<String> imageObjectKeys = reviewPhotoRepository.findByReviewId(reviewId).stream()
                .map(photo -> photo.getObjectKey())
                .toList();

        reviewRepository.delete(review);
        reviewRepository.flush();

        updateSpotReviewStats(spot);
        deleteImagesAfterCommit(imageObjectKeys);
    }

    private void updateSpotReviewStats(Spot spot) {
        List<Object[]> rows = reviewRepository.findAvgAndCountBySpotId(spot.getId());

        Object[] result = rows.isEmpty() ? null : rows.get(0);

        double average = result == null || result[0] == null ? 0.0 : ((Number) result[0]).doubleValue();

        int count = result == null || result[1] == null ? 0 : ((Number) result[1]).intValue();

        spot.updateReviewStats(average, count);
    }

    private void deleteImagesAfterCommit(List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteImages(objectKeys);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteImages(objectKeys);
                    }
                }
        );
    }

    private void deleteImages(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            try {
                imageStorageService.delete(objectKey);
            } catch (RuntimeException exception) {
                log.warn("관리자 리뷰 삭제 후 S3 이미지 정리에 실패했습니다. key={}", objectKey, exception);
            }
        }
    }
}
