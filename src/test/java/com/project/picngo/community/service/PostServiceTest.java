package com.project.picngo.community.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.AuthErrorCode;
import com.project.picngo.common.exception.code.CommunityErrorCode;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.common.image.dto.ImageUploadResult;
import com.project.picngo.common.image.dto.PhotoExifInfo;
import com.project.picngo.common.image.service.ExifExtractor;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.community.domain.Post;
import com.project.picngo.community.domain.PostImage;
import com.project.picngo.community.domain.PostWeather;
import com.project.picngo.community.domain.PostBookmark;
import com.project.picngo.community.domain.PostLike;
import com.project.picngo.community.domain.PostSort;
import com.project.picngo.community.dto.PostCreateRequest;
import com.project.picngo.community.dto.PostUpdateRequest;
import com.project.picngo.community.repository.*;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.spot.domain.Spot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostRepository postRepository;
    @Mock PostImageRepository imageRepository;
    @Mock PostLikeRepository likeRepository;
    @Mock PostBookmarkRepository bookmarkRepository;
    @Mock PostCommentRepository commentRepository;
    @Mock UserRepository userRepository;
    @Mock SpotRepository spotRepository;
    @Mock ExifExtractor exifExtractor;
    @Mock ImageStorageService imageStorageService;

    @InjectMocks PostService service;

    @Test
    @DisplayName("좋아요가 없으면 좋아요를 저장하고 게시글 좋아요 수를 1 증가시킨다")
    void likeIsIdempotent() {
        Post post = mock(Post.class);
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(likeRepository.existsByPostIdAndUserId(1L, 2L)).thenReturn(false);
        when(post.getLikeCount()).thenReturn(1L);

        var response = service.like(1L, 2L);

        assertEquals(true, response.active());
        assertEquals(1L, response.count());
        verify(likeRepository).save(any());
        verify(postRepository).changeLikeCount(1L, 1L);
    }

    @Test
    @DisplayName("좋아요하지 않은 게시글의 좋아요를 취소해도 카운트를 감소시키지 않는다")
    void unlikeDoesNotDecreaseCountWhenLikeDoesNotExist() {
        Post post = mock(Post.class);
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(likeRepository.findByPostIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        when(post.getLikeCount()).thenReturn(0L);

        var response = service.unlike(1L, 2L);

        assertEquals(false, response.active());
        assertEquals(0L, response.count());
        verify(postRepository, never()).changeLikeCount(any(), anyLong());
    }

    @Test
    @DisplayName("게시글 작성 요청에 이미지가 없으면 거부한다")
    void postWithoutImagesIsRejected() {
        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.createPost(2L, request(), List.of())
        );

        assertEquals(CommunityErrorCode.POST_IMAGE_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(userRepository, postRepository, imageStorageService);
    }

    @Test
    @DisplayName("팔로우 기능이 연결되기 전에는 팔로잉 피드를 명시적으로 거부한다")
    void followingFeedFailsUntilFollowFeatureIsConnected() {
        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.getPosts(null, PostSort.FOLLOWING, null, 0, 20)
        );

        assertEquals(CommunityErrorCode.FOLLOWING_FEED_NOT_AVAILABLE, exception.getErrorCode());
        verifyNoInteractions(postRepository);
    }

    @Test
    @DisplayName("존재하지 않는 게시글을 조회하면 POST_NOT_FOUND 예외를 반환한다")
    void missingPostReturnsNotFound() {
        when(postRepository.findById(404L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class, () -> service.getPost(404L, null));

        assertEquals(CommunityErrorCode.POST_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(imageRepository, likeRepository, bookmarkRepository);
    }

    @Test
    @DisplayName("게시글 작성자가 아니면 게시글을 수정할 수 없다")
    void updatePostRequiresAuthor() {
        Post post = mock(Post.class);
        User author = mock(User.class);
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(post.getAuthor()).thenReturn(author);
        when(author.getId()).thenReturn(2L);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.updatePost(
                        1L,
                        9L,
                        emptyUpdateRequest(null),
                        null
                )
        );

        assertEquals(CommunityErrorCode.POST_FORBIDDEN, exception.getErrorCode());
        verifyNoInteractions(imageRepository);
    }

    @Test
    @DisplayName("수정 요청에서 생략한 게시글 필드와 기존 이미지는 그대로 유지한다")
    void updatePostKeepsOmittedFieldsAndImages() {
        Post post = mock(Post.class);
        PostImage image = mock(PostImage.class);
        User author = mock(User.class);
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(post.getAuthor()).thenReturn(author);
        when(author.getId()).thenReturn(9L);
        when(post.getId()).thenReturn(1L);
        when(post.getTags()).thenReturn(List.of());
        when(image.getId()).thenReturn(10L);
        when(image.getObjectKey()).thenReturn("community/9/existing.jpg");
        when(imageRepository.findByPostIdOrderByPostOrderAsc(1L))
                .thenReturn(List.of(image));
        when(imageStorageService.getPresignedUrl("community/9/existing.jpg"))
                .thenReturn("presigned-url");

        service.updatePost(1L, 9L, emptyUpdateRequest(null), null);

        verify(post).update(null, null, null, null, null, null);
        verify(image).changePostOrder(0);
        verify(imageRepository, never()).deleteAll(any());
        verify(imageRepository, never()).save(any());
        verify(imageRepository).flush();
    }

    @Test
    @DisplayName("수정할 게시글에 속하지 않은 유지 이미지 ID는 거부한다")
    void updatePostRejectsForeignImageId() {
        Post post = mock(Post.class);
        PostImage image = mock(PostImage.class);
        User author = mock(User.class);
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(post.getAuthor()).thenReturn(author);
        when(author.getId()).thenReturn(9L);
        when(imageRepository.findByPostIdOrderByPostOrderAsc(1L))
                .thenReturn(List.of(image));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.updatePost(
                        1L,
                        9L,
                        emptyUpdateRequest(List.of(999L)),
                        null
                )
        );

        assertEquals(CommunityErrorCode.POST_IMAGE_INVALID, exception.getErrorCode());
        verify(imageRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("게시글 수정 시 유지 이미지 순서를 변경하고 삭제 이미지와 새 이미지를 함께 반영한다")
    void updatePostReordersRetainedImagesAndReplacesImages() {
        Post post = mock(Post.class);
        PostImage removedImage = mock(PostImage.class);
        PostImage retainedImage = mock(PostImage.class);
        User author = mock(User.class);
        MultipartFile newImage = imageFile();
        PhotoExifInfo exif = mock(PhotoExifInfo.class);

        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(post.getAuthor()).thenReturn(author);
        when(author.getId()).thenReturn(9L);
        when(post.getId()).thenReturn(1L);
        when(post.getTags()).thenReturn(List.of());
        when(removedImage.getId()).thenReturn(10L);
        when(removedImage.getObjectKey()).thenReturn("community/9/removed.jpg");
        when(retainedImage.getId()).thenReturn(20L);
        when(imageRepository.findByPostIdOrderByPostOrderAsc(1L))
                .thenReturn(List.of(removedImage, retainedImage), List.of());
        when(exifExtractor.extract(newImage)).thenReturn(exif);
        when(imageStorageService.upload(newImage, "community/9"))
                .thenReturn(new ImageUploadResult("community/9/new.jpg", "new-url"));
        when(imageRepository.save(any(PostImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PostUpdateRequest request = new PostUpdateRequest(
                "  updated content  ",
                null,
                null,
                null,
                null,
                null,
                List.of("  night  ", "night"),
                List.of(20L)
        );

        service.updatePost(1L, 9L, request, List.of(newImage));

        verify(post).update(
                "  updated content  ",
                null,
                null,
                null,
                null,
                List.of("night")
        );
        verify(retainedImage).changePostOrder(0);
        verify(imageRepository).deleteAll(List.of(removedImage));

        ArgumentCaptor<PostImage> imageCaptor = ArgumentCaptor.forClass(PostImage.class);
        verify(imageRepository).save(imageCaptor.capture());
        assertEquals("community/9/new.jpg", imageCaptor.getValue().getObjectKey());
        assertEquals(1, imageCaptor.getValue().getPostOrder());
        assertSame(post, imageCaptor.getValue().getPost());
        verify(imageStorageService).delete("community/9/removed.jpg");
    }

    @Test
    @DisplayName("게시글 수정 결과 이미지가 한 장도 없으면 거부한다")
    void updatePostRejectsRemovingEveryImage() {
        Post post = mock(Post.class);
        PostImage image = mock(PostImage.class);
        User author = mock(User.class);

        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(post.getAuthor()).thenReturn(author);
        when(author.getId()).thenReturn(9L);
        when(image.getId()).thenReturn(10L);
        when(imageRepository.findByPostIdOrderByPostOrderAsc(1L))
                .thenReturn(List.of(image));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.updatePost(1L, 9L, emptyUpdateRequest(List.of()), null)
        );

        assertEquals(CommunityErrorCode.POST_IMAGE_REQUIRED, exception.getErrorCode());
        verify(imageRepository, never()).deleteAll(any());
        verifyNoInteractions(imageStorageService);
    }

    @Test
    @DisplayName("게시글 수정 요청의 유지 이미지 ID가 중복되면 거부한다")
    void updatePostRejectsDuplicateRetainedImageIds() {
        Post post = mock(Post.class);
        PostImage image = mock(PostImage.class);
        User author = mock(User.class);

        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(post.getAuthor()).thenReturn(author);
        when(author.getId()).thenReturn(9L);
        when(imageRepository.findByPostIdOrderByPostOrderAsc(1L))
                .thenReturn(List.of(image));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.updatePost(
                        1L,
                        9L,
                        emptyUpdateRequest(List.of(10L, 10L)),
                        null
                )
        );

        assertEquals(CommunityErrorCode.POST_IMAGE_INVALID, exception.getErrorCode());
        verify(imageRepository, never()).deleteAll(any());
        verifyNoInteractions(imageStorageService);
    }

    @Test
    @DisplayName("게시글 수정 결과 이미지가 5장을 초과하면 거부한다")
    void updatePostRejectsMoreThanFiveFinalImages() {
        Post post = mock(Post.class);
        User author = mock(User.class);
        List<PostImage> existingImages = List.of(
                mock(PostImage.class),
                mock(PostImage.class),
                mock(PostImage.class),
                mock(PostImage.class),
                mock(PostImage.class)
        );

        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(post.getAuthor()).thenReturn(author);
        when(author.getId()).thenReturn(9L);
        when(imageRepository.findByPostIdOrderByPostOrderAsc(1L))
                .thenReturn(existingImages);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.updatePost(
                        1L,
                        9L,
                        emptyUpdateRequest(null),
                        List.of(imageFile())
                )
        );

        assertEquals(CommunityErrorCode.POST_IMAGE_TOO_MANY, exception.getErrorCode());
        verify(imageRepository, never()).save(any());
        verifyNoInteractions(imageStorageService);
    }

    @Test
    @DisplayName("게시글 작성자가 아니면 게시글을 삭제할 수 없다")
    void deletePostRequiresAuthor() {
        Post post = mock(Post.class);
        User author = mock(User.class);
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(post.getAuthor()).thenReturn(author);
        when(author.getId()).thenReturn(2L);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.deletePost(1L, 9L)
        );

        assertEquals(CommunityErrorCode.POST_FORBIDDEN, exception.getErrorCode());
        verifyNoInteractions(
                commentRepository,
                likeRepository,
                bookmarkRepository,
                imageRepository
        );
        verify(postRepository, never()).delete(any());
    }

    @Test
    @DisplayName("게시글 삭제 시 연관 데이터와 S3 이미지를 함께 정리한다")
    void deletePostRemovesRelationsAndStoredImages() {
        Post post = mock(Post.class);
        User author = mock(User.class);
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(post.getAuthor()).thenReturn(author);
        when(author.getId()).thenReturn(9L);
        when(imageRepository.findObjectKeysByPostId(1L))
                .thenReturn(List.of("first-key", "second-key"));

        service.deletePost(1L, 9L);

        verify(imageRepository).findObjectKeysByPostId(1L);
        verify(commentRepository).deleteAllByPostId(1L);
        verify(likeRepository).deleteAllByPostId(1L);
        verify(bookmarkRepository).deleteAllByPostId(1L);
        verify(imageRepository).deleteAllByPostId(1L);
        verify(postRepository).delete(post);
        verify(postRepository).flush();
        verify(imageStorageService).delete("first-key");
        verify(imageStorageService).delete("second-key");
    }

    @Test
    @DisplayName("EXIF 응답은 제조사를 제외하고 카메라와 렌즈 모델 및 촬영 정보를 반환한다")
    void exifResponseReturnsModelsAndPublishingFields() {
        Post post = mock(Post.class);
        PostImage image = mock(PostImage.class);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(imageRepository.findByPostIdOrderByPostOrderAsc(1L)).thenReturn(List.of(image));
        when(image.getCameraModel()).thenReturn("ILCE-7M4");
        when(image.getLensModel()).thenReturn("FE 24-70mm F2.8 GM");
        when(image.getSoftware()).thenReturn("Adobe Lightroom Classic 12.3");
        when(image.getLatitude()).thenReturn(35.153386);
        when(image.getLongitude()).thenReturn(129.118785);
        when(image.getOriginalFileName()).thenReturn("DSC03421.JPG");
        when(image.getFileSize()).thenReturn(8_400_000L);

        var response = service.getExif(1L).images().getFirst();

        assertEquals("ILCE-7M4", response.cameraModel());
        assertEquals("FE 24-70mm F2.8 GM", response.lensModel());
        assertEquals("Adobe Lightroom Classic 12.3", response.software());
        assertEquals(35.153386, response.latitude());
        assertEquals(129.118785, response.longitude());
        assertEquals("DSC03421.JPG", response.fileName());
        assertEquals(8_400_000L, response.fileSize());
    }

    @Test
    @DisplayName("게시글 작성 이미지가 5장을 초과하면 거부한다")
    void tooManyPostImagesAreRejected() {
        List<MultipartFile> images = List.of(
                imageFile(), imageFile(), imageFile(), imageFile(), imageFile(), imageFile()
        );

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.createPost(2L, request(), images)
        );

        assertEquals(CommunityErrorCode.POST_IMAGE_TOO_MANY, exception.getErrorCode());
        verifyNoInteractions(userRepository, postRepository, imageStorageService);
    }

    @Test
    @DisplayName("multipart 요청의 파일 순서대로 이미지를 업로드하고 게시글에 연결한다")
    void imagesAreUploadedAndAttachedInRequestOrder() {
        User author = mock(User.class);
        MultipartFile first = imageFile();
        MultipartFile second = imageFile();
        PhotoExifInfo exif = mock(PhotoExifInfo.class);
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(exifExtractor.extract(any())).thenReturn(exif);
        when(imageStorageService.upload(first, "community/2"))
                .thenReturn(new ImageUploadResult("first-key", "first-url"));
        when(imageStorageService.upload(second, "community/2"))
                .thenReturn(new ImageUploadResult("second-key", "second-url"));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageRepository.save(any(PostImage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageRepository.findByPostIdOrderByPostOrderAsc(null)).thenReturn(List.of());

        service.createPost(2L, request(), List.of(first, second));

        ArgumentCaptor<PostImage> imageCaptor = ArgumentCaptor.forClass(PostImage.class);
        verify(imageRepository, times(2)).save(imageCaptor.capture());
        List<PostImage> savedImages = imageCaptor.getAllValues();
        assertEquals("first-key", savedImages.get(0).getObjectKey());
        assertEquals(0, savedImages.get(0).getPostOrder());
        assertEquals("second-key", savedImages.get(1).getObjectKey());
        assertEquals(1, savedImages.get(1).getPostOrder());
        assertSame(savedImages.get(0).getPost(), savedImages.get(1).getPost());
        verify(imageRepository).flush();
    }

    @Test
    @DisplayName("여러 이미지 중 후속 업로드가 실패하면 앞서 업로드한 S3 객체를 삭제한다")
    void uploadedImagesAreDeletedWhenLaterUploadFails() {
        MultipartFile first = imageFile();
        MultipartFile second = imageFile();
        when(userRepository.findById(2L)).thenReturn(Optional.of(mock(User.class)));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(exifExtractor.extract(any())).thenReturn(mock(PhotoExifInfo.class));
        when(imageStorageService.upload(first, "community/2"))
                .thenReturn(new ImageUploadResult("first-key", "first-url"));
        when(imageStorageService.upload(second, "community/2"))
                .thenThrow(new RuntimeException("upload failed"));

        assertThrows(
                RuntimeException.class,
                () -> service.createPost(2L, request(), List.of(first, second))
        );

        verify(imageStorageService).delete("first-key");
    }

    @Test
    @DisplayName("게시글 작성 요청에 빈 이미지 파일이 포함되면 거부한다")
    void emptyImageFileIsRejected() {
        MultipartFile emptyImage = mock(MultipartFile.class);
        when(emptyImage.isEmpty()).thenReturn(true);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.createPost(2L, request(), List.of(emptyImage))
        );

        assertEquals(CommunityErrorCode.POST_IMAGE_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(userRepository, postRepository, imageStorageService);
    }

    @Test
    @DisplayName("게시글 작성 시 스팟과 촬영 정보를 함께 저장하고 카메라와 렌즈는 null을 허용한다")
    void postStoresSpotAndShootingInformation() {
        User author = mock(User.class);
        Spot spot = mock(Spot.class);
        MultipartFile image = imageFile();
        LocalTime shootingTime = LocalTime.of(5, 30);
        PostCreateRequest request = new PostCreateRequest(
                "content",
                9L,
                shootingTime,
                PostWeather.PARTLY_CLOUDY,
                null,
                null,
                List.of("야경명소")
        );
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(spotRepository.findById(9L)).thenReturn(Optional.of(spot));
        when(exifExtractor.extract(image)).thenReturn(mock(PhotoExifInfo.class));
        when(imageStorageService.upload(image, "community/2"))
                .thenReturn(new ImageUploadResult("image-key", "image-url"));
        when(postRepository.save(any(Post.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(imageRepository.save(any(PostImage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageRepository.findByPostIdOrderByPostOrderAsc(null)).thenReturn(List.of());

        service.createPost(2L, request, List.of(image));

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        Post savedPost = postCaptor.getValue();
        assertSame(spot, savedPost.getSpot());
        assertEquals(shootingTime, savedPost.getShootingTime());
        assertEquals(PostWeather.PARTLY_CLOUDY, savedPost.getWeather());
        assertEquals(null, savedPost.getCameraModel());
        assertEquals(null, savedPost.getLensModel());
    }

    @Test
    @DisplayName("존재하지 않는 스팟 ID로는 게시글을 작성할 수 없다")
    void missingSpotIsRejected() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(mock(User.class)));
        when(spotRepository.findById(404L)).thenReturn(Optional.empty());
        PostCreateRequest request = new PostCreateRequest(
                "content",
                404L,
                LocalTime.of(5, 30),
                PostWeather.CLEAR,
                null,
                null,
                List.of()
        );

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.createPost(2L, request, List.of(imageFile()))
        );

        assertEquals(SpotErrorCode.SPOT_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(imageRepository);
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 좋아요한 게시글에 다시 좋아요해도 중복 저장하거나 카운트를 증가시키지 않는다")
    void repeatedLikeDoesNotInsertOrIncreaseCounterAgain() {
        Post post = mock(Post.class);
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(likeRepository.existsByPostIdAndUserId(1L, 2L)).thenReturn(true);
        when(post.getLikeCount()).thenReturn(7L);

        var response = service.like(1L, 2L);

        assertEquals(7L, response.count());
        verify(likeRepository, never()).save(any());
        verify(postRepository, never()).changeLikeCount(any(), anyLong());
    }

    @Test
    @DisplayName("기존 좋아요를 취소하면 좋아요 데이터를 삭제하고 카운트를 1 감소시킨다")
    void existingLikeIsDeletedAndCounterIsDecreased() {
        Post post = mock(Post.class);
        PostLike like = mock(PostLike.class);
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(likeRepository.findByPostIdAndUserId(1L, 2L)).thenReturn(Optional.of(like));
        when(post.getLikeCount()).thenReturn(6L);

        var response = service.unlike(1L, 2L);

        assertEquals(6L, response.count());
        verify(likeRepository).delete(like);
        verify(postRepository).changeLikeCount(1L, -1L);
    }

    @Test
    @DisplayName("이미 북마크한 게시글을 다시 북마크해도 중복 저장하거나 카운트를 증가시키지 않는다")
    void repeatedBookmarkDoesNotInsertOrIncreaseCounterAgain() {
        Post post = mock(Post.class);
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(bookmarkRepository.existsByPostIdAndUserId(1L, 2L)).thenReturn(true);
        when(post.getBookmarkCount()).thenReturn(3L);

        var response = service.bookmark(1L, 2L);

        assertEquals(3L, response.count());
        verify(bookmarkRepository, never()).save(any(PostBookmark.class));
        verify(postRepository, never()).changeBookmarkCount(any(), anyLong());
    }

    @Test
    @DisplayName("음수 페이지는 0으로, 100을 초과하는 페이지 크기는 100으로 보정한다")
    void pageAndSizeAreNormalizedToSafeRange() {
        when(postRepository.search(isNull(), isNull(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(2);
                    return new PageImpl<>(List.of(), pageable, 0);
                });

        service.getPosts(null, PostSort.LATEST, "  ", -5, 1000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).search(isNull(), isNull(), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(100, pageableCaptor.getValue().getPageSize());
    }

    @Test
    @DisplayName("내가 쓴 글은 로그인한 작성자 ID로 검색한다")
    void myPostsFiltersByAuthenticatedAuthor() {
        when(postRepository.search(isNull(), eq(9L), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(2), 0));

        service.getPosts(9L, PostSort.MY_POSTS, null, 0, 20);

        verify(postRepository).search(isNull(), eq(9L), any(Pageable.class));
    }

    @Test
    @DisplayName("비로그인 사용자는 내가 쓴 글을 조회할 수 없다")
    void myPostsRequiresAuthentication() {
        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.getPosts(null, PostSort.MY_POSTS, null, 0, 20)
        );

        assertEquals(AuthErrorCode.LOGIN_REQUIRED, exception.getErrorCode());
        assertEquals("로그인이 필요한 기능입니다.", exception.getMessage());
        verifyNoInteractions(postRepository);
    }

    @Test
    @DisplayName("피드의 이미지와 사용자 반응을 게시글별 반복 조회하지 않고 각각 한 번에 조회한다")
    void feedLoadsImagesAndReactionsInBatches() {
        Post firstPost = feedPost(1L);
        Post secondPost = feedPost(2L);
        when(postRepository.search(isNull(), isNull(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(firstPost, secondPost),
                        invocation.getArgument(2),
                        2
                ));
        when(imageRepository.findAllByPostIds(List.of(1L, 2L))).thenReturn(List.of());
        when(likeRepository.findLikedPostIds(9L, List.of(1L, 2L))).thenReturn(List.of(1L));
        when(bookmarkRepository.findBookmarkedPostIds(9L, List.of(1L, 2L))).thenReturn(List.of(2L));

        var response = service.getPosts(9L, PostSort.LATEST, null, 0, 20);

        assertEquals(true, response.posts().get(0).liked());
        assertEquals(false, response.posts().get(0).bookmarked());
        assertEquals(false, response.posts().get(1).liked());
        assertEquals(true, response.posts().get(1).bookmarked());
        verify(imageRepository, times(1)).findAllByPostIds(List.of(1L, 2L));
        verify(likeRepository, times(1)).findLikedPostIds(9L, List.of(1L, 2L));
        verify(bookmarkRepository, times(1)).findBookmarkedPostIds(9L, List.of(1L, 2L));
        verify(imageRepository, never()).findByPostIdOrderByPostOrderAsc(anyLong());
        verify(likeRepository, never()).existsByPostIdAndUserId(anyLong(), anyLong());
        verify(bookmarkRepository, never()).existsByPostIdAndUserId(anyLong(), anyLong());
    }

    private PostCreateRequest request() {
        return new PostCreateRequest(
                "content",
                null,
                LocalTime.of(5, 30),
                PostWeather.CLEAR,
                null,
                null,
                List.of()
        );
    }

    private PostUpdateRequest emptyUpdateRequest(List<Long> retainedImageIds) {
        return new PostUpdateRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                retainedImageIds
        );
    }

    private MultipartFile imageFile() {
        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        return image;
    }

    private Post feedPost(Long postId) {
        Post post = mock(Post.class);
        when(post.getId()).thenReturn(postId);
        when(post.getTags()).thenReturn(List.of());
        when(post.getAuthor()).thenReturn(mock(User.class));
        return post;
    }
}
