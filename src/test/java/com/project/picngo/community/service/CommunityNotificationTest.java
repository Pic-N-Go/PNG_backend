package com.project.picngo.community.service;

import com.project.picngo.common.image.dto.ImageUploadResult;
import com.project.picngo.common.image.dto.PhotoExifInfo;
import com.project.picngo.common.image.service.ExifExtractor;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.community.domain.Post;
import com.project.picngo.community.domain.PostComment;
import com.project.picngo.community.domain.PostWeather;
import com.project.picngo.community.dto.CommentCreateRequest;
import com.project.picngo.community.dto.PostCreateRequest;
import com.project.picngo.community.repository.PostBookmarkRepository;
import com.project.picngo.community.repository.PostCommentLikeRepository;
import com.project.picngo.community.repository.PostCommentRepository;
import com.project.picngo.community.repository.PostImageRepository;
import com.project.picngo.community.repository.PostLikeRepository;
import com.project.picngo.community.repository.PostRepository;
import com.project.picngo.notification.service.NotificationService;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.FollowRepository;
import com.project.picngo.user.repository.UserRepository;
import com.project.picngo.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommunityNotificationTest {

    @Mock PostRepository postRepository;
    @Mock PostImageRepository imageRepository;
    @Mock PostLikeRepository likeRepository;
    @Mock PostBookmarkRepository bookmarkRepository;
    @Mock PostCommentRepository commentRepository;
    @Mock PostCommentLikeRepository commentLikeRepository;
    @Mock UserRepository userRepository;
    @Mock FollowRepository followRepository;
    @Mock SpotRepository spotRepository;
    @Mock ExifExtractor exifExtractor;
    @Mock ImageStorageService imageStorageService;
    @Mock NotificationService notificationService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock com.project.picngo.auth.service.RefreshTokenService refreshTokenService;

    private PostService postService;
    private PostCommentService postCommentService;
    private UserService userService;

    private User author;
    private User commenter;
    private User followerUser;
    private Post post;
    private Spot spot;

    @BeforeEach
    void setUp() {
        postService = new PostService(
                postRepository,
                imageRepository,
                likeRepository,
                bookmarkRepository,
                commentRepository,
                commentLikeRepository,
                userRepository,
                followRepository,
                spotRepository,
                exifExtractor,
                imageStorageService,
                notificationService
        );

        postCommentService = new PostCommentService(
                postRepository,
                commentRepository,
                commentLikeRepository,
                userRepository,
                imageStorageService,
                notificationService
        );

        userService = new UserService(
                userRepository,
                followRepository,
                postRepository,
                passwordEncoder,
                imageStorageService,
                refreshTokenService,
                notificationService
        );

        author = mock(User.class);
        when(author.getId()).thenReturn(1L);
        when(author.getNickname()).thenReturn("작성자");

        commenter = mock(User.class);
        when(commenter.getId()).thenReturn(2L);
        when(commenter.getNickname()).thenReturn("댓글러");

        followerUser = mock(User.class);
        when(followerUser.getId()).thenReturn(3L);
        when(followerUser.getNickname()).thenReturn("팔로워");

        spot = mock(Spot.class);
        when(spot.getId()).thenReturn(100L);

        post = mock(Post.class);
        when(post.getId()).thenReturn(10L);
        when(post.getAuthor()).thenReturn(author);
        when(post.getSpot()).thenReturn(spot);
        when(post.getContent()).thenReturn("경복궁의 가을 야경 사진입니다.");
    }

    @Nested
    @DisplayName("① 내 게시글에 새 댓글 등록 알림")
    class CommentNotificationTest {

        @Test
        @DisplayName("타인이 내 글에 댓글 작성 시 게시글 작성자에게 COMMUNITY_COMMENT 알림이 발송된다")
        void notifyPostAuthorOnNewComment() {
            when(postRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(post));
            when(userRepository.findById(2L)).thenReturn(Optional.of(commenter));

            PostComment comment = mock(PostComment.class);
            when(comment.getId()).thenReturn(101L);
            when(comment.getAuthor()).thenReturn(commenter);
            when(comment.getContent()).thenReturn("정말 멋진 사진이네요!");
            when(commentRepository.save(any(PostComment.class))).thenReturn(comment);

            CommentCreateRequest request = new CommentCreateRequest("정말 멋진 사진이네요!", null);
            postCommentService.createComment(10L, 2L, request);

            verify(notificationService).sendPushNotification(
                    eq(1L),
                    eq("COMMUNITY_COMMENT"),
                    eq("새 댓글 알림"),
                    eq("댓글러님이 회원님의 게시글에 댓글을 남겼습니다: 정말 멋진 사진이네요!"),
                    eq("/community/post/10"),
                    eq(100L)
            );
        }

        @Test
        @DisplayName("본인이 본인 글에 댓글 작성 시에는 알림이 발송되지 않는다")
        void doNotNotifyWhenAuthorCommentsOnOwnPost() {
            when(postRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(post));
            when(userRepository.findById(1L)).thenReturn(Optional.of(author));

            PostComment comment = mock(PostComment.class);
            when(comment.getId()).thenReturn(102L);
            when(comment.getAuthor()).thenReturn(author);
            when(comment.getContent()).thenReturn("추가 설명입니다.");
            when(commentRepository.save(any(PostComment.class))).thenReturn(comment);

            CommentCreateRequest request = new CommentCreateRequest("추가 설명입니다.", null);
            postCommentService.createComment(10L, 1L, request);

            verify(notificationService, never()).sendPushNotification(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("② 내 댓글에 새 답글(대댓글) 등록 알림")
    class ReplyNotificationTest {

        @Test
        @DisplayName("타인이 내 댓글에 답글을 작성하면 원댓글 작성자에게 COMMUNITY_REPLY 알림이 발송된다")
        void notifyParentCommentAuthorOnNewReply() {
            when(postRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(post));
            when(userRepository.findById(3L)).thenReturn(Optional.of(followerUser));

            PostComment parentComment = mock(PostComment.class);
            when(parentComment.getId()).thenReturn(101L);
            when(parentComment.getAuthor()).thenReturn(commenter);
            when(parentComment.isReply()).thenReturn(false);
            when(commentRepository.findByIdAndPostId(101L, 10L)).thenReturn(Optional.of(parentComment));

            PostComment reply = mock(PostComment.class);
            when(reply.getId()).thenReturn(103L);
            when(reply.getAuthor()).thenReturn(followerUser);
            when(reply.getContent()).thenReturn("저도 동감합니다!");
            when(commentRepository.save(any(PostComment.class))).thenReturn(reply);

            CommentCreateRequest request = new CommentCreateRequest("저도 동감합니다!", 101L);
            postCommentService.createComment(10L, 3L, request);

            // 원댓글 작성자(commenter - 2L)에게 답글 알림
            verify(notificationService).sendPushNotification(
                    eq(2L),
                    eq("COMMUNITY_REPLY"),
                    eq("새 답글 알림"),
                    eq("팔로워님이 회원님의 댓글에 답글을 남겼습니다: 저도 동감합니다!"),
                    eq("/community/post/10"),
                    eq(100L)
            );

            // 게시글 작성자(author - 1L)에게도 새 댓글 알림
            verify(notificationService).sendPushNotification(
                    eq(1L),
                    eq("COMMUNITY_COMMENT"),
                    eq("새 댓글 알림"),
                    eq("팔로워님이 회원님의 게시글에 댓글을 남겼습니다: 저도 동감합니다!"),
                    eq("/community/post/10"),
                    eq(100L)
            );
        }
    }

    @Nested
    @DisplayName("③ 내 글에 좋아요 클릭 알림")
    class LikeNotificationTest {

        @Test
        @DisplayName("타인이 내 글에 좋아요를 누르면 게시글 작성자에게 COMMUNITY_LIKE 알림이 발송된다")
        void notifyPostAuthorOnLike() {
            when(postRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(post));
            when(postRepository.findById(10L)).thenReturn(Optional.of(post));
            when(likeRepository.existsByPostIdAndUserId(10L, 2L)).thenReturn(false);
            when(userRepository.findById(2L)).thenReturn(Optional.of(commenter));
            when(post.getLikeCount()).thenReturn(1L);

            postService.like(10L, 2L);

            verify(notificationService).sendPushNotification(
                    eq(1L),
                    eq("COMMUNITY_LIKE"),
                    eq("게시글 좋아요"),
                    eq("댓글러님이 회원님의 게시글을 좋아합니다."),
                    eq("/community/post/10"),
                    eq(100L),
                    contains("LIKE:2:10:")
            );
        }

        @Test
        @DisplayName("본인이 누른 좋아요는 알림이 발송되지 않는다")
        void doNotNotifyWhenAuthorLikesOwnPost() {
            when(postRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(post));
            when(postRepository.findById(10L)).thenReturn(Optional.of(post));
            when(likeRepository.existsByPostIdAndUserId(10L, 1L)).thenReturn(false);
            when(post.getLikeCount()).thenReturn(1L);

            postService.like(10L, 1L);

            verify(notificationService, never()).sendPushNotification(any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("④ 유저 팔로우 알림")
    class FollowNotificationTest {

        @Test
        @DisplayName("유저를 팔로우하면 대상 유저에게 COMMUNITY_FOLLOW 알림이 발송된다")
        void notifyUserOnFollow() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(commenter));
            when(userRepository.findById(1L)).thenReturn(Optional.of(author));
            when(followRepository.existsByFollowerAndFollowing(commenter, author)).thenReturn(false);

            userService.follow(2L, 1L);

            verify(notificationService).sendPushNotification(
                    eq(1L),
                    eq("COMMUNITY_FOLLOW"),
                    eq("새 팔로워 알림"),
                    eq("댓글러님이 회원님을 팔로우하기 시작했습니다."),
                    eq("/users/2"),
                    isNull(),
                    contains("FOLLOW:2:1:")
            );
        }
    }

    @Nested
    @DisplayName("⑤ 작가 새 글 등록 시 팔로워 전원 알림 (Fan-out)")
    class NewPostFanOutNotificationTest {

        @Test
        @DisplayName("새 게시글을 작성하면 작가의 모든 팔로워에게 COMMUNITY_NEW_POST 알림이 발송된다")
        void notifyAllFollowersOnNewPost() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(author));
            when(spotRepository.findById(100L)).thenReturn(Optional.of(spot));
            when(postRepository.save(any(Post.class))).thenAnswer(i -> {
                Post p = i.getArgument(0);
                ReflectionTestUtils.setField(p, "id", 20L);
                return p;
            });
            when(followRepository.findFollowerUserIdsByFollowingId(1L)).thenReturn(List.of(2L, 3L));

            MockMultipartFile file = new MockMultipartFile(
                    "images", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3}
            );
            when(imageStorageService.upload(any(), anyString()))
                    .thenReturn(new ImageUploadResult("community/1/photo.jpg", "https://s3.example.com/photo.jpg"));
            when(exifExtractor.extract(any())).thenReturn(mock(PhotoExifInfo.class));

            PostCreateRequest request = new PostCreateRequest(
                    "새로운 출사 명소 리뷰입니다.",
                    100L,
                    LocalTime.of(18, 30),
                    PostWeather.CLEAR,
                    "Sony A7M4",
                    "24-70 GM",
                    List.of("야경", "출사")
            );

            postService.createPost(1L, request, List.of(file));

            // 팔로워 2L에게 발송 확인
            verify(notificationService).sendPushNotification(
                    eq(2L),
                    eq("COMMUNITY_NEW_POST"),
                    eq("새 게시글 알림"),
                    eq("작성자님이 새 글을 등록했습니다: 새로운 출사 명소 리뷰입니다."),
                    eq("/community/post/20"),
                    eq(100L)
            );

            // 팔로워 3L에게 발송 확인
            verify(notificationService).sendPushNotification(
                    eq(3L),
                    eq("COMMUNITY_NEW_POST"),
                    eq("새 게시글 알림"),
                    eq("작성자님이 새 글을 등록했습니다: 새로운 출사 명소 리뷰입니다."),
                    eq("/community/post/20"),
                    eq(100L)
            );
        }
    }
}
