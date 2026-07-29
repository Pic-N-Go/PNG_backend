package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ReviewErrorCode;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.common.image.dto.ImageUploadResult;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.domain.enums.ReviewTag;
import com.project.picngo.spot.domain.ReviewPhoto;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.dto.MyReviewListResponse;
import com.project.picngo.spot.dto.ReviewListResponse;
import com.project.picngo.spot.dto.ReviewPhotoResponse;
import com.project.picngo.spot.dto.ReviewRequest;
import com.project.picngo.spot.dto.ReviewResponse;
import com.project.picngo.spot.repository.ReviewPhotoRepository;
import com.project.picngo.spot.repository.ReviewRepository;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ReviewService {

    private static final int MAX_REVIEW_PHOTO_COUNT = 5;
    private static final int MAX_EQUIPMENT_INFO_LENGTH = 100; // Review.equipmentInfo 컬럼 길이와 동일해야 한다

    private final ReviewRepository reviewRepository;
    private final ReviewPhotoRepository reviewPhotoRepository;
    private final SpotRepository spotRepository;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;

    public ReviewListResponse getReviews(Long spotId, String sort, int page, int size) {
        if (!spotRepository.existsById(spotId)) {
            throw new CustomException(SpotErrorCode.SPOT_NOT_FOUND);
        }

        Pageable pageable = toPageable(sort, page, size);
        Page<Review> reviewPage = reviewRepository.findBySpotId(spotId, pageable);

        List<Object[]> avgAndCountList = reviewRepository.findAvgAndCountBySpotId(spotId);
        Double avgRating = null;
        if (avgAndCountList != null && !avgAndCountList.isEmpty()) {
            avgRating = (Double) avgAndCountList.get(0)[0];
        }
        Map<Integer, Long> distribution = buildDistribution(spotId);

        List<Review> reviews = reviewPage.getContent();
        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();
        List<Long> userIds = reviews.stream().map(Review::getUserId).toList();

        // profileImageUrl은 null이 정상 케이스라 Collectors.toMap의 값으로 쓰면 NPE가 난다. User째로 담는다.
        Map<Long, User> userMap = userRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, List<ReviewPhotoResponse>> photoMap = photosByReviewId(reviewIds);
        Map<Long, Set<ReviewTag>> tagMap = tagsByReviewId(reviewIds);

        List<ReviewListResponse.ReviewInfo> reviewInfos = reviews.stream()
                .map(review -> {
                    User user = userMap.get(review.getUserId());
                    return ReviewListResponse.ReviewInfo.of(
                            review,
                            user != null ? user.getNickname() : "알 수 없음",
                            user != null ? user.getProfileImageUrl() : null,
                            tagMap.getOrDefault(review.getId(), Set.of()),
                            photoMap.getOrDefault(review.getId(), List.of())
                    );
                })
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

    public MyReviewListResponse getMyReviews(Long userId, String sort, int page, int size) {
        Pageable pageable = toPageable(sort, page, size);
        Page<Review> reviewPage = reviewRepository.findByUserIdWithSpot(userId, pageable);

        List<Review> reviews = reviewPage.getContent();
        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();
        Map<Long, List<ReviewPhotoResponse>> photoMap = photosByReviewId(reviewIds);
        Map<Long, Set<ReviewTag>> tagMap = tagsByReviewId(reviewIds);

        return new MyReviewListResponse(
                reviews.stream()
                        .map(review -> MyReviewListResponse.MyReviewInfo.of(
                                review,
                                tagMap.getOrDefault(review.getId(), Set.of()),
                                photoMap.getOrDefault(review.getId(), List.of())
                        ))
                        .toList(),
                reviewPage.getTotalElements(),
                reviewPage.getTotalPages(),
                reviewPage.getNumber()
        );
    }

    // 수정 화면 진입 시 폼을 채울 원본값. 스팟 상세의 myReviewId로 받은 id를 그대로 쓴다.
    public ReviewResponse getMyReview(Long userId, Long reviewId) {
        Review review = findMyReview(userId, reviewId);
        return ReviewResponse.from(review, tagsOf(reviewId), photosOf(reviewId));
    }

    @Transactional
    public ReviewResponse createReview(Long userId, Long spotId, ReviewRequest request, List<MultipartFile> photos) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        // 스팟당 1인 1리뷰. 프론트에서 막아도 API 직접 호출로 우회 가능하므로 서버에서 막는다.
        if (reviewRepository.existsBySpotIdAndUserId(spotId, userId)) {
            throw new CustomException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
        }

        validatePhotoCount(0, photos);

        Review review = Review.builder()
                .spot(spot)
                .userId(userId)
                .rating(request.rating())
                .content(request.content())
                .equipmentInfo(joinEquipmentInfo(request.equipmentInfo()))
                .timePeriod(request.timePeriod())
                .visitedAt(request.visitedAt())
                .tags(request.tags())
                .build();

        Review savedReview = reviewRepository.save(review);
        List<String> uploadedKeys = new ArrayList<>();

        try {
            List<ReviewPhotoResponse> uploaded = uploadReviewPhotos(savedReview, photos, uploadedKeys);

            updateSpotReviewStats(spot);

            return ReviewResponse.from(savedReview, request.tags() == null ? Set.of() : request.tags(), uploaded);
        } catch (RuntimeException e) {
            deleteUploadedImages(uploadedKeys);
            throw e;
        }
    }

    @Transactional
    public ReviewResponse updateReview(Long userId, Long reviewId, ReviewRequest request) {
        Review review = findMyReview(userId, reviewId);
        review.update(
                request.rating(),
                request.content(),
                joinEquipmentInfo(request.equipmentInfo()),
                request.timePeriod(),
                request.visitedAt(),
                request.tags()
        );
        // 별점이 바뀌면 스팟 평균도 다시 계산해야 한다. 빠지면 스팟 상세에 옛 평점이 남는다.
        updateSpotReviewStats(review.getSpot());

        // photos 없이 반환하면 프론트가 응답을 그대로 반영할 때 사진이 사라진 것처럼 보인다.
        return ReviewResponse.from(review, tagsOf(reviewId), photosOf(reviewId));
    }

    // 사진 추가. PUT은 JSON이라 파일 파트를 받을 수 없어 별도 엔드포인트로 분리했다.
    @Transactional
    public List<ReviewPhotoResponse> addReviewPhotos(Long userId, Long reviewId, List<MultipartFile> photos) {
        Review review = findMyReview(userId, reviewId);
        validatePhotoCount(reviewPhotoRepository.countByReviewId(reviewId), photos);

        List<String> uploadedKeys = new ArrayList<>();
        try {
            uploadReviewPhotos(review, photos, uploadedKeys);
            return photosOf(reviewId);
        } catch (RuntimeException e) {
            deleteUploadedImages(uploadedKeys);
            throw e;
        }
    }

    @Transactional
    public void deleteReviewPhoto(Long userId, Long reviewId, Long photoId) {
        findMyReview(userId, reviewId);

        // 다른 리뷰의 사진 id를 조용히 통과시키면 지운 줄 알았던 사진이 남는다. 404로 막는다.
        ReviewPhoto photo = reviewPhotoRepository.findById(photoId)
                .filter(saved -> saved.getReview().getId().equals(reviewId))
                .orElseThrow(() -> new CustomException(ReviewErrorCode.REVIEW_PHOTO_NOT_FOUND));

        reviewPhotoRepository.delete(photo);
        reviewPhotoRepository.flush();

        deleteUploadedImages(List.of(photo.getPhotoUrl()));
    }

    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = findMyReview(userId, reviewId);
        Spot spot = review.getSpot();

        List<String> photoKeys = reviewPhotoRepository.findByReviewId(reviewId).stream()
                .map(ReviewPhoto::getPhotoUrl)
                .toList();

        reviewRepository.delete(review);
        reviewRepository.flush();

        deleteUploadedImages(photoKeys);

        updateSpotReviewStats(spot);
    }

    private Review findMyReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ReviewErrorCode.REVIEW_NOT_FOUND));
        if (!review.getUserId().equals(userId)) {
            throw new CustomException(ReviewErrorCode.REVIEW_FORBIDDEN);
        }
        return review;
    }

    // 범위 밖 page/size는 PageRequest.of가 IllegalArgumentException을 던져 500이 된다. 여기서 잘라낸다.
    static Pageable toPageable(String sort, int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), toSort(sort));
    }

    // 동점 행 순서는 DB가 보장하지 않아 페이지 경계에서 중복·누락이 생긴다. id를 타이브레이커로 고정한다.
    static Sort toSort(String sort) {
        Sort tieBreaker = Sort.by(Sort.Direction.DESC, "id");
        return switch (sort) {
            case "LATEST" -> Sort.by(Sort.Direction.DESC, "createdAt").and(tieBreaker);
            case "RATING_HIGH" -> Sort.by(Sort.Direction.DESC, "rating").and(tieBreaker);
            case "RATING_LOW" -> Sort.by(Sort.Direction.ASC, "rating").and(tieBreaker);
            default -> throw new CustomException(ReviewErrorCode.REVIEW_INVALID_SORT);
        };
    }

    private void updateSpotReviewStats(Spot spot) {
        List<Object[]> rows = reviewRepository.findAvgAndCountBySpotId(spot.getId());
        Double avg = (rows != null && !rows.isEmpty()) ? (Double) rows.get(0)[0] : null;
        int count = (rows != null && !rows.isEmpty() && rows.get(0)[1] != null)
                ? ((Long) rows.get(0)[1]).intValue() : 0;
        spot.updateReviewStats(avg, count);
    }

    // 지연 로딩에 맡기면 리뷰 행마다 review_tag를 조회한다(페이지 20건이면 20회). 한 번에 가져와 묶는다.
    private Map<Long, Set<ReviewTag>> tagsByReviewId(List<Long> reviewIds) {
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        return reviewRepository.findTagsByReviewIds(reviewIds).stream()
                .collect(Collectors.groupingBy(
                        row -> (Long) row[0],
                        // HashSet이면 응답 배열 순서가 요청마다 달라진다. 조회 순서를 유지한다.
                        Collectors.mapping(row -> (ReviewTag) row[1],
                                Collectors.toCollection(java.util.LinkedHashSet::new))
                ));
    }

    private Map<Long, List<ReviewPhotoResponse>> photosByReviewId(List<Long> reviewIds) {
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        return reviewPhotoRepository.findByReview_IdInOrderByIdAsc(reviewIds).stream()
                .collect(Collectors.groupingBy(
                        photo -> photo.getReview().getId(),
                        Collectors.mapping(this::toPhotoResponse, Collectors.toList())
                ));
    }

    private ReviewPhotoResponse toPhotoResponse(ReviewPhoto photo) {
        return new ReviewPhotoResponse(photo.getId(), imageStorageService.getPresignedUrl(photo.getPhotoUrl()));
    }

    private Set<ReviewTag> tagsOf(Long reviewId) {
        return tagsByReviewId(List.of(reviewId)).getOrDefault(reviewId, Set.of());
    }

    private List<ReviewPhotoResponse> photosOf(Long reviewId) {
        return photosByReviewId(List.of(reviewId)).getOrDefault(reviewId, List.of());
    }

    private Map<Integer, Long> buildDistribution(Long spotId) {
        Map<Integer, Long> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) distribution.put(i, 0L);
        reviewRepository.findRatingDistributionBySpotId(spotId)
                .forEach(row -> distribution.put((Integer) row[0], (Long) row[1]));
        return distribution;
    }

    private List<ReviewPhotoResponse> uploadReviewPhotos(Review review, List<MultipartFile> photos, List<String> uploadedKeys) {
        if (countUploadable(photos) == 0) {
            return List.of();
        }

        List<ReviewPhotoResponse> uploaded = new ArrayList<>();

        for (MultipartFile photo : photos) {
            if (photo == null || photo.isEmpty()) {
                continue;
            }

            ImageUploadResult uploadResult = imageStorageService.upload(photo, "reviews/" + review.getId());
            uploadedKeys.add(uploadResult.key());

            ReviewPhoto reviewPhoto = ReviewPhoto.builder()
                    .review(review)
                    .photoUrl(uploadResult.key())
                    .build();

            reviewPhotoRepository.save(reviewPhoto);
            uploaded.add(new ReviewPhotoResponse(reviewPhoto.getId(), uploadResult.url()));
        }

        return uploaded;
    }

    // 빈 파트는 업로드에서 건너뛰므로 개수 검증도 같은 기준으로 센다.
    static int countUploadable(List<MultipartFile> photos) {
        if (photos == null) {
            return 0;
        }
        return (int) photos.stream().filter(photo -> photo != null && !photo.isEmpty()).count();
    }

    // 기존 사진 + 신규 파일 합계로 상한을 검증한다. 작성은 기존 0장.
    static void validatePhotoCount(int existingCount, List<MultipartFile> newPhotos) {
        if (existingCount + countUploadable(newPhotos) > MAX_REVIEW_PHOTO_COUNT) {
            throw new CustomException(ReviewErrorCode.REVIEW_PHOTO_TOO_MANY);
        }
    }

    private void deleteUploadedImages(List<String> uploadedKeys) {
        for (String uploadedKey : uploadedKeys) {
            try {
                imageStorageService.delete(uploadedKey);
            } catch (RuntimeException e) {
                log.warn("이미지 삭제에 실패했습니다. key={}", uploadedKey, e);;
            }
        }
    }

    // ", "로 합쳐 한 컬럼에 저장하므로 요소별 길이 제한(@Size)으로는 컬럼 초과를 막을 수 없다.
    // 검증 없이 넘기면 DataIntegrityViolationException → 500이 되므로 합친 길이로 400을 낸다.
    static String joinEquipmentInfo(List<String> equipmentInfo) {
        if (equipmentInfo == null || equipmentInfo.isEmpty()) {
            return null;
        }

        String joined = equipmentInfo.stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(", "));
        if (joined.length() > MAX_EQUIPMENT_INFO_LENGTH) {
            throw new CustomException(ReviewErrorCode.REVIEW_EQUIPMENT_INFO_TOO_LONG);
        }
        return joined.isBlank() ? null : joined;
    }
}
