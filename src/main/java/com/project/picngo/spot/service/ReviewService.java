package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ReviewErrorCode;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.domain.ReviewPhoto;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.dto.ReviewListResponse;
import com.project.picngo.spot.dto.ReviewRequest;
import com.project.picngo.spot.dto.ReviewResponse;
import com.project.picngo.spot.repository.ReviewPhotoRepository;
import com.project.picngo.spot.repository.ReviewRepository;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    // ponytail: Spring Security 연동 전까지 하드코딩
    private static final Long TEMP_USER_ID = 1L;

    private final ReviewRepository reviewRepository;
    private final ReviewPhotoRepository reviewPhotoRepository;
    private final SpotRepository spotRepository;
    private final UserRepository userRepository;

    public ReviewListResponse getReviews(Long spotId, String sort, int page, int size) {
        if (!spotRepository.existsById(spotId)) {
            throw new CustomException(SpotErrorCode.SPOT_NOT_FOUND);
        }

        Pageable pageable = PageRequest.of(page, Math.min(size, 100), toSort(sort));
        Page<Review> reviewPage = reviewRepository.findBySpotId(spotId, pageable);

        Object[] avgAndCount = reviewRepository.findAvgAndCountBySpotId(spotId);
        Double avgRating = (Double) avgAndCount[0];
        Map<Integer, Long> distribution = buildDistribution(spotId);

        List<Review> reviews = reviewPage.getContent();
        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();
        List<Long> userIds = reviews.stream().map(Review::getUserId).toList();

        Map<Long, String> nicknameMap = userRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getNickname()));
        Map<Long, List<ReviewPhoto>> photoMap = reviewPhotoRepository.findByReview_IdIn(reviewIds).stream()
                .collect(Collectors.groupingBy(p -> p.getReview().getId()));

        List<ReviewListResponse.ReviewInfo> reviewInfos = reviews.stream()
                .map(review -> ReviewListResponse.ReviewInfo.of(
                        review,
                        nicknameMap.getOrDefault(review.getUserId(), "알 수 없음"),
                        photoMap.getOrDefault(review.getId(), List.of())
                ))
                .toList();

        return new ReviewListResponse(
                new ReviewListResponse.SummaryInfo(
                        avgRating != null ? Math.round(avgRating * 10) / 10.0 : 0.0,
                        reviewPage.getTotalElements(),
                        distribution
                ),
                new ReviewListResponse.PageInfo(
                        reviewInfos,
                        reviewPage.getTotalElements(),
                        reviewPage.getTotalPages(),
                        reviewPage.getNumber()
                )
        );
    }

    @Transactional
    public ReviewResponse createReview(Long spotId, ReviewRequest request) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        Review review = Review.builder()
                .spot(spot)
                .userId(TEMP_USER_ID)
                .rating(request.rating())
                .content(request.content())
                .equipmentInfo(request.equipmentInfo())
                .visitedAt(request.visitedAt())
                .build();

        return ReviewResponse.from(reviewRepository.save(review));
    }

    @Transactional
    public ReviewResponse updateReview(Long reviewId, ReviewRequest request) {
        Review review = findMyReview(reviewId);
        review.update(request.rating(), request.content(), request.equipmentInfo(), request.visitedAt());
        return ReviewResponse.from(review);
    }

    @Transactional
    public void deleteReview(Long reviewId) {
        Review review = findMyReview(reviewId);
        reviewRepository.delete(review);
    }

    private Review findMyReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ReviewErrorCode.REVIEW_NOT_FOUND));
        if (!review.getUserId().equals(TEMP_USER_ID)) {
            throw new CustomException(ReviewErrorCode.REVIEW_FORBIDDEN);
        }
        return review;
    }

    private Sort toSort(String sort) {
        return switch (sort) {
            case "RATING_HIGH" -> Sort.by(Sort.Direction.DESC, "rating");
            case "RATING_LOW" -> Sort.by(Sort.Direction.ASC, "rating");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private Map<Integer, Long> buildDistribution(Long spotId) {
        Map<Integer, Long> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) distribution.put(i, 0L);
        reviewRepository.findRatingDistributionBySpotId(spotId)
                .forEach(row -> distribution.put((Integer) row[0], (Long) row[1]));
        return distribution;
    }
}
