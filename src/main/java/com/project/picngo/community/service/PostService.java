package com.project.picngo.community.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.AuthErrorCode;
import com.project.picngo.common.exception.code.CommunityErrorCode;
import com.project.picngo.common.exception.code.ImageErrorCode;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.common.image.domain.ExifConsentStatus;
import com.project.picngo.common.image.dto.ImageUploadResult;
import com.project.picngo.common.image.dto.PhotoExifInfo;
import com.project.picngo.common.image.dto.PhotoExifResponse;
import com.project.picngo.common.image.service.ExifExtractor;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.community.domain.*;
import com.project.picngo.community.dto.*;
import com.project.picngo.community.repository.*;
import com.project.picngo.notification.service.NotificationService;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.FollowRepository;
import com.project.picngo.user.repository.UserRepository;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PostService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_POST_IMAGE_COUNT = 5;

    private final PostRepository postRepository;
    private final PostImageRepository imageRepository;
    private final PostLikeRepository likeRepository;
    private final PostBookmarkRepository bookmarkRepository;
    private final PostCommentRepository commentRepository;
    private final PostCommentLikeRepository commentLikeRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final SpotRepository spotRepository;
    private final ExifExtractor exifExtractor;
    private final ImageStorageService imageStorageService;
    private final NotificationService notificationService;

    /**
     * @param authorId 지정하면 그 사용자가 쓴 글만 준다(프로필 화면의 게시글 탭).
     *                 POPULAR·LATEST에만 적용된다. MY_POSTS·FOLLOWING·BOOKMARKED는 대상 글이
     *                 "내 글"·"팔로우한 사람의 글"·"내가 저장한 글"로 이미 정해져 있어 이 값을 무시한다
     *                 (섞어 쓸 화면이 없다 — 프로필 게시글 탭은 LATEST + authorId로 조회한다).
     */
    public PostPageResponse getPosts(Long userId, PostSort sort, String keyword, Long authorId, int page, int size) {

        if ((sort == PostSort.MY_POSTS || sort == PostSort.FOLLOWING || sort == PostSort.BOOKMARKED) && userId == null) {
            throw new CustomException(AuthErrorCode.LOGIN_REQUIRED);
        }
        // 너무 큰 사이즈 요청이 올 경우 100으로 조정
        int normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizedSize, toSort(sort));
        String normalizedKeyword = normalize(keyword);

        Page<Post> result = switch (sort) {
            case MY_POSTS -> postRepository.search(normalizedKeyword, userId, pageable);
            case POPULAR, LATEST -> postRepository.search(normalizedKeyword, authorId, pageable);
            case FOLLOWING -> postRepository.searchFollowing(normalizedKeyword, userId, pageable);
            case BOOKMARKED -> postRepository.searchBookmarked(normalizedKeyword, userId, pageable);
        };
        List<PostResponse> posts = toFeedResponses(result.getContent(), userId);

        return new PostPageResponse(
                posts,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize(),
                result.hasNext()
        );
    }

    public PostResponse getPost(Long postId, Long userId) {
        return toResponse(findPost(postId), userId);
    }

    @Transactional
    public PostResponse createPost(Long userId, PostCreateRequest request, List<MultipartFile> images) {
        validateExifConsent(request.technicalExifConsent(), request.locationExifConsent());
        validatePostImages(images);

        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        Spot spot = request.spotId() == null ? null : spotRepository.findById(request.spotId())
                        .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        Post post = postRepository.save(Post.create(
                author,
                request.content().trim(),
                spot,
                request.shootingTime(),
                request.weather(),
                request.cameraModel(),
                request.lensModel(),
                normalizeTags(request.tags()),
                request.technicalExifConsent(),
                request.locationExifConsent()
        ));

        List<String> uploadedKeys = new ArrayList<>();
        try {
            for (int index = 0; index < images.size(); index++) {
                MultipartFile file = images.get(index);
                PhotoExifInfo exif = exifExtractor.extract(file, post.getTechnicalExifConsent(), post.getLocationExifConsent());
                ImageUploadResult uploaded = imageStorageService.upload(file, "community/" + userId);
                uploadedKeys.add(uploaded.key());

                PostImage image = PostImage.uploaded(userId, uploaded.key(), exif);
                image.attachTo(post, index);
                imageRepository.save(image);
            }
            imageRepository.flush();

            // 🔔 팔로워(구독자) 대상 새 글 알림 비동기 큐 Fan-out
            sendNewPostNotificationsToFollowers(author, post);

            return toResponse(post, userId);
            //오류 발생 시 s3 업로드 삭제, 트랜잭션 롤백으로 데이터 정합성 보장
        } catch (RuntimeException exception) {
            deleteUploadedImages(uploadedKeys);
            throw exception;
        }
    }

    @Transactional
    public ReactionResponse like(Long postId, Long userId) {
        Post post = findPostForUpdate(postId);
        if (!likeRepository.existsByPostIdAndUserId(postId, userId)) {
            likeRepository.save(new PostLike(post, userId));
            postRepository.changeLikeCount(postId, 1);

            // 🔔 게시글 좋아요 알림 (본인 좋아요 제외)
            if (post.getAuthor() != null && !post.getAuthor().getId().equals(userId)) {
                User liker = userRepository.findById(userId).orElse(null);
                String likerNickname = liker != null ? liker.getNickname() : "회원";
                Long spotId = post.getSpot() != null ? post.getSpot().getId() : null;
                String dedupeKey = "LIKE:" + userId + ":" + postId + ":" + java.time.LocalDate.now();

                notificationService.sendPushNotification(
                        post.getAuthor().getId(),
                        "COMMUNITY_LIKE",
                        "게시글 좋아요",
                        likerNickname + "님이 회원님의 게시글을 좋아합니다.",
                        "/community/post/" + postId,
                        spotId,
                        dedupeKey
                );
            }
        }
        return new ReactionResponse(true, findPost(postId).getLikeCount());
    }

    @Transactional
    public ReactionResponse unlike(Long postId, Long userId) {
        findPostForUpdate(postId);
        likeRepository.findByPostIdAndUserId(postId, userId).ifPresent(like -> {
            likeRepository.delete(like);
            postRepository.changeLikeCount(postId, -1);
        });
        return new ReactionResponse(false, findPost(postId).getLikeCount());
    }

    @Transactional
    public ReactionResponse bookmark(Long postId, Long userId) {
        Post post = findPostForUpdate(postId);
        if (!bookmarkRepository.existsByPostIdAndUserId(postId, userId)) {
            bookmarkRepository.save(new PostBookmark(post, userId));
            postRepository.changeBookmarkCount(postId, 1);
        }
        return new ReactionResponse(true, findPost(postId).getBookmarkCount());
    }

    @Transactional
    public ReactionResponse removeBookmark(Long postId, Long userId) {
        findPostForUpdate(postId);
        bookmarkRepository.findByPostIdAndUserId(postId, userId).ifPresent(bookmark -> {
            bookmarkRepository.delete(bookmark);
            postRepository.changeBookmarkCount(postId, -1);
        });
        return new ReactionResponse(false, findPost(postId).getBookmarkCount());
    }

    @Transactional
    public PostResponse updatePost(Long postId, Long userId, PostUpdateRequest request, List<MultipartFile> newImages) {
        Post post = findPostForUpdate(postId);
        validatePostAuthor(post, userId);

        if (request.spotId() != null) {
            Spot spot = spotRepository.findById(request.spotId())
                    .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));
            post.changeSpot(spot);
        }

        List<String> normalizedTags = request.tags() == null ? null : normalizeTags(request.tags());

        post.update(request.content(), request.shootingTime(), request.weather(), request.cameraModel(), request.lensModel(), normalizedTags);

        List<PostImage> existingImages = imageRepository.findByPostIdOrderByPostOrderAsc(postId);

        List<MultipartFile> files = newImages == null ? List.of() : newImages;

        validateNewImageFiles(files);

        List<PostImage> retainedImages = resolveRetainedImages(existingImages, request.retainedImageIds());

        validateFinalImageCount(retainedImages.size() + files.size());

        Set<Long> retainedImageIds = retainedImages.stream()
                .map(PostImage::getId)
                .collect(Collectors.toSet());

        List<PostImage> removedImages = existingImages.stream()
                .filter(image -> !retainedImageIds.contains(image.getId()))
                .toList();

        List<String> removedObjectKeys = removedImages.stream()
                .map(PostImage::getObjectKey)
                .toList();

        for (int index = 0; index < retainedImages.size(); index++) {
            retainedImages.get(index).changePostOrder(index);
        }

        List<String> newlyUploadedKeys = new ArrayList<>();
        try {
            for (int index = 0; index < files.size(); index++) {
                MultipartFile file = files.get(index);

                PhotoExifInfo exif = exifExtractor.extract(file, post.getTechnicalExifConsent(), post.getLocationExifConsent());

                ImageUploadResult uploaded = imageStorageService.upload(file, "community/" + userId);

                newlyUploadedKeys.add(uploaded.key());

                PostImage image = PostImage.uploaded(userId, uploaded.key(), exif);

                image.attachTo(post, retainedImages.size() + index);

                imageRepository.save(image);
            }

            if (!removedImages.isEmpty()) {
                imageRepository.deleteAll(removedImages);
            }

            imageRepository.flush();

            deleteImagesAfterCommit(removedObjectKeys);

            return toResponse(post, userId);

        } catch (RuntimeException exception) {
            deleteUploadedImages(newlyUploadedKeys);
            throw exception;
        }
    }

    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = findPostForUpdate(postId);
        validatePostAuthor(post, userId);

        List<String> imageObjectKeys = imageRepository.findObjectKeysByPostId(postId);

        // 댓글·좋아요·북마크는 데이터가 많아질 수 있으므로 JPA Cascade 대신 게시글 ID 기준 일괄 삭제 쿼리를 사용
        // 댓글 좋아요는 댓글을 참조하므로 반드시 댓글보다 먼저 지운다(FK 위반 방지).
        commentLikeRepository.deleteAllByPostId(postId);
        commentRepository.deleteAllByPostId(postId);
        likeRepository.deleteAllByPostId(postId);
        bookmarkRepository.deleteAllByPostId(postId);
        imageRepository.deleteAllByPostId(postId);
        postRepository.delete(post);
        postRepository.flush();

        deleteImagesAfterCommit(imageObjectKeys);
    }

    public PostExifResponse getExif(Long postId) {
        findPost(postId);
        List<PhotoExifResponse> exif = imageRepository.findByPostIdOrderByPostOrderAsc(postId).stream()
                .map(this::toExifResponse)
                .toList();
        return new PostExifResponse(postId, exif);
    }

    // ===== 내부 헬퍼 메서드 ======

    private PhotoExifResponse toExifResponse(PostImage image) {
        return new PhotoExifResponse(
                image.getId(),
                image.getCameraModel(),
                image.getLensModel(),
                image.getIso(),
                image.getFNumber(),
                image.getExposureTime(),
                image.getFocalLength(),
                image.getExposureMode(),
                image.getMeteringMode(),
                image.getWhiteBalance(),
                image.getFlash(),
                image.getFocalLength35mm(),
                image.getSoftware(),
                image.getLatitude(),
                image.getLongitude(),
                image.getAddress(),
                image.getFileSize(),
                image.getFileFormat(),
                image.getOriginalFileName()
        );
    }

    private Post findPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));
    }

    // 비관적 쓰기 잠금 적용 (동시성 문제)
    private Post findPostForUpdate(Long postId) {
        return postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));
    }

    private PostResponse toResponse(Post post, Long userId) {
        List<PostImageResponse> images = imageRepository.findByPostIdOrderByPostOrderAsc(post.getId()).stream()
                .map(image -> new PostImageResponse(
                        image.getId(),
                        imageStorageService.getPresignedUrl(image.getObjectKey()),
                        image.getImageWidth(),
                        image.getImageHeight()
                ))
                .toList();

        boolean liked = userId != null && likeRepository.existsByPostIdAndUserId(post.getId(), userId);
        boolean bookmarked = userId != null && bookmarkRepository.existsByPostIdAndUserId(post.getId(), userId);

        return buildResponse(post, images, liked, bookmarked);
    }

    private List<PostResponse> toFeedResponses(List<Post> posts, Long userId) {
        if (posts.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = posts.stream()
                .map(Post::getId)
                .toList();

        Map<Long, List<PostImageResponse>> imagesByPostId = imageRepository.findAllByPostIds(postIds).stream()
                .collect(Collectors.groupingBy(
                        image -> image.getPost().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(this::toImageResponse, Collectors.toList())
                ));

        Set<Long> likedPostIds = userId == null ? Collections.emptySet() : new HashSet<>(likeRepository.findLikedPostIds(userId, postIds));

        Set<Long> bookmarkedPostIds = userId == null ? Collections.emptySet() : new HashSet<>(bookmarkRepository.findBookmarkedPostIds(userId, postIds));

        return posts.stream()
                .map(post -> buildResponse(
                        post,
                        imagesByPostId.getOrDefault(post.getId(), List.of()),
                        likedPostIds.contains(post.getId()),
                        bookmarkedPostIds.contains(post.getId())
                ))
                .toList();
    }

    private PostImageResponse toImageResponse(PostImage image) {
        return new PostImageResponse(
                image.getId(),
                imageStorageService.getPresignedUrl(image.getObjectKey()),
                image.getImageWidth(),
                image.getImageHeight()
        );
    }

    private PostResponse buildResponse(Post post, List<PostImageResponse> images, boolean liked, boolean bookmarked) {
        return new PostResponse(
                post.getId(),
                post.getContent(),
                post.getSpot() == null ? null : post.getSpot().getId(),
                post.getSpot() == null ? null : post.getSpot().getName(),
                post.getShootingTime(),
                post.getWeather(),
                post.getCameraModel(),
                post.getLensModel(),
                List.copyOf(post.getTags()),
                PostAuthorResponse.from(post.getAuthor(), imageStorageService.getPresignedUrl(post.getAuthor().getDisplayProfileImage())),
                images,
                post.getLikeCount(),
                post.getCommentCount(),
                post.getBookmarkCount(),
                liked,
                bookmarked,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    // 게시글 정렬 기준
    private Sort toSort(PostSort sort) {
        return switch (sort) {
            case POPULAR -> Sort.by(
                    Sort.Order.desc("likeCount"),
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );
            case MY_POSTS, LATEST, FOLLOWING, BOOKMARKED -> Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );
        };
    }

    // 키워드 정제화
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // 태그 입력 정제
    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .toList();
    }

    private List<PostImage> resolveRetainedImages(
            List<PostImage> existingImages,
            List<Long> requestedImageIds
    ) {
        if (requestedImageIds == null) {
            return new ArrayList<>(existingImages);
        }

        if (new HashSet<>(requestedImageIds).size() != requestedImageIds.size()) {
            throw new CustomException(CommunityErrorCode.POST_IMAGE_INVALID);
        }

        Map<Long, PostImage> existingImageMap = existingImages.stream()
                .collect(Collectors.toMap(
                        PostImage::getId,
                        image -> image
                ));
        List<PostImage> retainedImages = new ArrayList<>();

        for (Long imageId : requestedImageIds) {
            PostImage image = existingImageMap.get(imageId);
            if (image == null) {
                throw new CustomException(CommunityErrorCode.POST_IMAGE_INVALID);
            }
            retainedImages.add(image);
        }

        return retainedImages;
    }

    private void validatePostAuthor(Post post, Long userId
    ) {
        if (userId == null || !Objects.equals(post.getAuthor().getId(), userId)) {
            throw new CustomException(CommunityErrorCode.POST_FORBIDDEN);
        }
    }

    private void validateExifConsent(ExifConsentStatus technicalConsent, ExifConsentStatus locationConsent) {
        if (!isConsentDecision(technicalConsent) || !isConsentDecision(locationConsent)) {
            throw new CustomException(ImageErrorCode.INVALID_EXIF_CONSENT);
        }
    }

    private boolean isConsentDecision(ExifConsentStatus status) {
        return status == ExifConsentStatus.GRANTED || status == ExifConsentStatus.DECLINED;
    }

    // 이미지 입력 정제 최소 1개, 최대 5개 제한
    private void validatePostImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()
                || images.stream().anyMatch(image -> image == null || image.isEmpty())) {
            throw new CustomException(CommunityErrorCode.POST_IMAGE_REQUIRED);
        }
        if (images.size() > MAX_POST_IMAGE_COUNT) {
            throw new CustomException(CommunityErrorCode.POST_IMAGE_TOO_MANY);
        }
    }

    private void validateNewImageFiles(List<MultipartFile> images) {
        if (images.stream().anyMatch(image -> image == null || image.isEmpty())) {
            throw new CustomException(CommunityErrorCode.POST_IMAGE_REQUIRED);
        }
    }

    private void validateFinalImageCount(int imageCount) {
        if (imageCount < 1) {
            throw new CustomException(CommunityErrorCode.POST_IMAGE_REQUIRED);
        }
        if (imageCount > MAX_POST_IMAGE_COUNT) {
            throw new CustomException(CommunityErrorCode.POST_IMAGE_TOO_MANY);
        }
    }

    private void deleteImagesAfterCommit(List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteUploadedImages(objectKeys);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteUploadedImages(objectKeys);
                    }
                }
        );
    }

    // 게시글 작성 실패 시 이미 업로드된 S3 이미지 보상 삭제
    private void deleteUploadedImages(List<String> uploadedKeys) {
        for (String uploadedKey : uploadedKeys) {
            try {
                imageStorageService.delete(uploadedKey);
            } catch (RuntimeException cleanupException) {
                log.warn(
                        "S3 이미지 정리 중 삭제에 실패했습니다. key={}",
                        uploadedKey,
                        cleanupException
                );
            }
        }
    }

    private void sendNewPostNotificationsToFollowers(User author, Post post) {
        try {
            List<Long> followerIds = followRepository.findFollowerUserIdsByFollowingId(author.getId());
            if (!followerIds.isEmpty()) {
                String postSnippet = post.getContent().length() > 30
                        ? post.getContent().substring(0, 30) + "..."
                        : post.getContent();
                Long spotId = post.getSpot() != null ? post.getSpot().getId() : null;

                for (Long followerId : followerIds) {
                    notificationService.sendPushNotification(
                            followerId,
                            "COMMUNITY_NEW_POST",
                            "새 게시글 알림",
                            author.getNickname() + "님이 새 글을 등록했습니다: " + postSnippet,
                            "/community/post/" + post.getId(),
                            spotId
                    );
                }
            }
        } catch (Exception e) {
            log.warn("게시글 등록 알림 발송 중 예외 발생 (authorId: {}, postId: {})", author.getId(), post.getId(), e);
        }
    }
}
