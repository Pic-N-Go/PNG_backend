package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ReviewErrorCode;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.domain.ReviewPhoto;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.ReviewPhotoRepository;
import com.project.picngo.spot.repository.ReviewRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewPhotoRepository reviewPhotoRepository;

    @Mock
    private ImageStorageService imageStorageService;

    @InjectMocks
    private AdminReviewService adminReviewService;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteReviewByAdminDeletesReviewUpdatesSpotStatsAndDeletesImagesAfterCommit() {
        Long reviewId = 1L;
        Review review = org.mockito.Mockito.mock(Review.class);
        Spot spot = org.mockito.Mockito.mock(Spot.class);
        ReviewPhoto firstPhoto = org.mockito.Mockito.mock(ReviewPhoto.class);
        ReviewPhoto secondPhoto = org.mockito.Mockito.mock(ReviewPhoto.class);

        when(reviewRepository.findByIdForUpdate(reviewId)).thenReturn(Optional.of(review));
        when(review.getSpot()).thenReturn(spot);
        when(spot.getId()).thenReturn(10L);
        when(reviewPhotoRepository.findByReviewId(reviewId)).thenReturn(List.of(firstPhoto, secondPhoto));
        when(firstPhoto.getObjectKey()).thenReturn("reviews/first.jpg");
        when(secondPhoto.getObjectKey()).thenReturn("reviews/second.jpg");
        when(reviewRepository.findAvgAndCountBySpotId(10L))
                .thenReturn(List.<Object[]>of(new Object[]{4.5, 2L}));
        TransactionSynchronizationManager.initSynchronization();

        adminReviewService.deleteReviewByAdmin(reviewId);

        verify(reviewRepository).delete(review);
        verify(reviewRepository).flush();
        verify(spot).updateReviewStats(4.5, 2);
        verify(imageStorageService, never()).delete("reviews/first.jpg");

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(imageStorageService).delete("reviews/first.jpg");
        verify(imageStorageService).delete("reviews/second.jpg");
    }

    @Test
    void deleteReviewByAdminThrowsWhenReviewDoesNotExist() {
        when(reviewRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminReviewService.deleteReviewByAdmin(1L))
                .isInstanceOf(CustomException.class)
                .hasMessage(ReviewErrorCode.REVIEW_NOT_FOUND.getMessage());

        verify(reviewRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteLastReviewResetsSpotStats() {
        Review review = org.mockito.Mockito.mock(Review.class);
        Spot spot = org.mockito.Mockito.mock(Spot.class);
        when(reviewRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(review));
        when(review.getSpot()).thenReturn(spot);
        when(spot.getId()).thenReturn(10L);
        when(reviewPhotoRepository.findByReviewId(1L)).thenReturn(List.of());
        when(reviewRepository.findAvgAndCountBySpotId(10L)).thenReturn(List.of());

        adminReviewService.deleteReviewByAdmin(1L);

        verify(spot).updateReviewStats(0.0, 0);
    }
}
