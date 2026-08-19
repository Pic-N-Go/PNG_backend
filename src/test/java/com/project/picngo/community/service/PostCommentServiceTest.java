package com.project.picngo.community.service;

import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.CommunityErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.community.domain.Post;
import com.project.picngo.community.domain.PostComment;
import com.project.picngo.community.domain.PostCommentLike;
import com.project.picngo.community.dto.CommentCreateRequest;
import com.project.picngo.community.dto.CommentUpdateRequest;
import com.project.picngo.community.repository.PostRepository;
import com.project.picngo.community.repository.PostCommentLikeRepository;
import com.project.picngo.community.repository.PostCommentRepository;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostCommentServiceTest {

    @Mock PostRepository postRepository;
    @Mock PostCommentRepository commentRepository;
    @Mock PostCommentLikeRepository commentLikeRepository;
    @Mock UserRepository userRepository;
    @Mock ImageStorageService imageStorageService;

    @InjectMocks PostCommentService service;

    @Test
    @DisplayName("존재하지 않는 게시글의 댓글 목록을 조회하면 POST_NOT_FOUND 예외를 반환한다")
    void commentsOfMissingPostAreRejected() {
        when(postRepository.existsById(404L)).thenReturn(false);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.getComments(404L, null, 0, 20)
        );

        assertEquals(CommunityErrorCode.POST_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(commentRepository);
    }

    @Test
    @DisplayName("댓글 목록의 음수 페이지와 과도한 페이지 크기를 안전한 범위로 보정한다")
    void commentPageAndSizeAreNormalized() {
        when(postRepository.existsById(1L)).thenReturn(true);
        when(commentRepository.findByPostIdAndParentIsNull(eq(1L), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));

        service.getComments(1L, null, -1, 1000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findByPostIdAndParentIsNull(eq(1L), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(100, pageableCaptor.getValue().getPageSize());
    }

    @Test
    @DisplayName("최상위 댓글은 좋아요 많은 순으로, 동점이면 오래된 순으로 정렬한다")
    void topLevelCommentsAreSortedByLikes() {
        when(postRepository.existsById(1L)).thenReturn(true);
        when(commentRepository.findByPostIdAndParentIsNull(eq(1L), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));

        service.getComments(1L, null, 0, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findByPostIdAndParentIsNull(eq(1L), captor.capture());
        assertEquals(
                Sort.by(Sort.Order.desc("likeCount"), Sort.Order.asc("createdAt"), Sort.Order.asc("id")),
                captor.getValue().getSort()
        );
    }

    @Test
    @DisplayName("답글은 좋아요와 무관하게 작성 시각 오름차순으로 정렬한다")
    void repliesStaySortedByTime() {
        PostComment parent = new PostComment(mock(Post.class), null, mock(User.class), "parent");
        when(commentRepository.findByIdAndPostId(10L, 1L)).thenReturn(Optional.of(parent));
        when(commentRepository.findByParentId(eq(parent.getId()), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));

        service.getReplies(1L, 10L, null, 0, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findByParentId(eq(parent.getId()), captor.capture());
        assertEquals(Sort.by(Sort.Direction.ASC, "createdAt"), captor.getValue().getSort());
    }

    @Test
    @DisplayName("존재하지 않는 게시글에는 댓글을 작성할 수 없다")
    void commentCannotBeCreatedForMissingPost() {
        when(postRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.createComment(404L, 2L, new CommentCreateRequest("comment", null))
        );

        assertEquals(CommunityErrorCode.POST_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(userRepository, commentRepository);
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 댓글을 작성할 수 없다")
    void missingUserCannotCreateComment() {
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(Post.class)));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.createComment(1L, 404L, new CommentCreateRequest("comment", null))
        );

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(commentRepository);
        verify(postRepository, never()).incrementCommentCount(any());
    }

    @Test
    @DisplayName("댓글 작성에 성공하면 공백을 제거해 저장하고 게시글 댓글 수를 증가시킨다")
    void commentIsTrimmedAndPostCountIsIncreased() {
        Post post = mock(Post.class);
        User author = mock(User.class);
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(commentRepository.save(any(PostComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createComment(1L, 2L, new CommentCreateRequest("  hello  ", null));

        assertEquals("hello", response.content());
        verify(commentRepository).save(any(PostComment.class));
        verify(postRepository).incrementCommentCount(1L);
    }

    @Test
    @DisplayName("댓글 작성자는 댓글 내용을 수정할 수 있고 앞뒤 공백은 제거된다")
    void commentAuthorCanUpdateContent() {
        User author = mock(User.class);
        when(author.getId()).thenReturn(2L);
        PostComment comment = new PostComment(mock(Post.class), null, author, "before");
        when(commentRepository.findByIdAndPostId(10L, 1L))
                .thenReturn(Optional.of(comment));

        var response = service.updateComment(
                1L,
                10L,
                2L,
                new CommentUpdateRequest("  after  ")
        );

        assertEquals("after", response.content());
        assertEquals("after", comment.getContent());
    }

    @Test
    @DisplayName("댓글 작성자가 아니면 댓글을 수정할 수 없다")
    void updateCommentRequiresAuthor() {
        User author = mock(User.class);
        when(author.getId()).thenReturn(2L);
        PostComment comment = new PostComment(mock(Post.class), null, author, "before");
        when(commentRepository.findByIdAndPostId(10L, 1L))
                .thenReturn(Optional.of(comment));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.updateComment(
                        1L,
                        10L,
                        9L,
                        new CommentUpdateRequest("after")
                )
        );

        assertEquals(CommunityErrorCode.COMMENT_FORBIDDEN, exception.getErrorCode());
        assertEquals("before", comment.getContent());
    }

    @Test
    @DisplayName("요청한 게시글에 속한 댓글이 없으면 COMMENT_NOT_FOUND를 반환한다")
    void updateCommentRejectsCommentFromAnotherPost() {
        when(commentRepository.findByIdAndPostId(10L, 1L))
                .thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.updateComment(
                        1L,
                        10L,
                        2L,
                        new CommentUpdateRequest("after")
                )
        );

        assertEquals(CommunityErrorCode.COMMENT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("댓글 작성자가 댓글을 삭제하면 게시글 댓글 수를 감소시킨다")
    void commentAuthorCanDeleteComment() {
        User author = mock(User.class);
        when(author.getId()).thenReturn(2L);
        PostComment comment = new PostComment(mock(Post.class), null, author, "comment");
        when(commentRepository.findByIdAndPostId(10L, 1L))
                .thenReturn(Optional.of(comment));

        when(commentRepository.findByParentId(eq(comment.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.deleteComment(1L, 10L, 2L);

        verify(commentRepository).delete(comment);
        // 답글이 없으므로 자기 자신 1개만 줄인다.
        verify(postRepository).changeCommentCount(1L, -1);
    }

    @Test
    @DisplayName("댓글 작성자가 아니면 댓글을 삭제할 수 없다")
    void deleteCommentRequiresAuthor() {
        User author = mock(User.class);
        when(author.getId()).thenReturn(2L);
        PostComment comment = new PostComment(mock(Post.class), null, author, "comment");
        when(commentRepository.findByIdAndPostId(10L, 1L))
                .thenReturn(Optional.of(comment));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.deleteComment(1L, 10L, 9L)
        );

        assertEquals(CommunityErrorCode.COMMENT_FORBIDDEN, exception.getErrorCode());
        verify(commentRepository, never()).delete(any());
        verify(postRepository, never()).changeCommentCount(any(), anyLong());
    }

    // ── 답글 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("parentId를 주면 답글로 저장하고 원 댓글의 답글 수를 증가시킨다")
    void replyIsAttachedToParentAndCountsUp() {
        Post post = mock(Post.class);
        User author = mock(User.class);
        PostComment parent = new PostComment(post, null, author, "parent");
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(commentRepository.findByIdAndPostId(10L, 1L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(PostComment.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.createComment(1L, 2L, new CommentCreateRequest("reply", 10L));

        assertEquals("reply", response.content());
        verify(commentRepository).changeReplyCount(parent.getId(), 1);
        // 답글도 게시글 댓글 수에 포함된다.
        verify(postRepository).incrementCommentCount(1L);
    }

    @Test
    @DisplayName("답글에 답글을 달면 그 답글의 원 댓글에 붙어 깊이가 1단계로 유지된다")
    void replyToReplyIsFlattenedToTopLevelParent() {
        Post post = mock(Post.class);
        User author = mock(User.class);
        PostComment parent = new PostComment(post, null, author, "parent");
        PostComment reply = new PostComment(post, parent, author, "reply");
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(commentRepository.findByIdAndPostId(11L, 1L)).thenReturn(Optional.of(reply));
        when(commentRepository.save(any(PostComment.class))).thenAnswer(i -> i.getArgument(0));

        ArgumentCaptor<PostComment> captor = ArgumentCaptor.forClass(PostComment.class);
        service.createComment(1L, 2L, new CommentCreateRequest("reply of reply", 11L));

        verify(commentRepository).save(captor.capture());
        // 답글(reply)이 아니라 그 부모(parent)에 붙어야 한다.
        assertEquals(parent, captor.getValue().getParent());
    }

    @Test
    @DisplayName("다른 게시글의 댓글을 부모로 지정하면 COMMENT_NOT_FOUND를 반환한다")
    void replyToCommentOfAnotherPostIsRejected() {
        when(postRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(Post.class)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(mock(User.class)));
        when(commentRepository.findByIdAndPostId(99L, 1L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.createComment(1L, 2L, new CommentCreateRequest("reply", 99L))
        );

        assertEquals(CommunityErrorCode.COMMENT_NOT_FOUND, exception.getErrorCode());
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("답글이 달린 댓글을 삭제하면 답글도 함께 지우고 게시글 댓글 수를 그만큼 줄인다")
    void deletingParentAlsoDeletesRepliesAndCounts() {
        Post post = mock(Post.class);
        User author = mock(User.class);
        when(author.getId()).thenReturn(2L);
        PostComment parent = new PostComment(post, null, author, "parent");
        when(commentRepository.findByIdAndPostId(10L, 1L)).thenReturn(Optional.of(parent));
        when(commentRepository.findByParentId(eq(parent.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        new PostComment(post, parent, author, "r1"),
                        new PostComment(post, parent, author, "r2")
                )));

        service.deleteComment(1L, 10L, 2L);

        verify(commentRepository).deleteAllByParentId(parent.getId());
        verify(commentRepository).delete(parent);
        // 답글 2개 + 자기 자신 1개
        verify(postRepository).changeCommentCount(1L, -3);
    }

    @Test
    @DisplayName("답글을 삭제하면 원 댓글의 답글 수를 감소시킨다")
    void deletingReplyDecrementsParentReplyCount() {
        Post post = mock(Post.class);
        User author = mock(User.class);
        when(author.getId()).thenReturn(2L);
        PostComment parent = new PostComment(post, null, author, "parent");
        PostComment reply = new PostComment(post, parent, author, "reply");
        when(commentRepository.findByIdAndPostId(11L, 1L)).thenReturn(Optional.of(reply));
        when(commentRepository.findByParentId(eq(reply.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.deleteComment(1L, 11L, 2L);

        verify(commentRepository).changeReplyCount(parent.getId(), -1);
        verify(postRepository).changeCommentCount(1L, -1);
    }

    // ── 댓글 좋아요 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("댓글 좋아요를 누르면 좋아요를 저장하고 카운트를 올린다")
    void likeSavesAndCountsUp() {
        PostComment comment = new PostComment(mock(Post.class), null, mock(User.class), "c");
        when(commentRepository.findByIdAndPostIdForUpdate(10L, 1L)).thenReturn(Optional.of(comment));
        when(commentRepository.findByIdAndPostId(10L, 1L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.existsByCommentIdAndUserId(10L, 2L)).thenReturn(false);

        var response = service.like(1L, 10L, 2L);

        assertTrue(response.active());
        verify(commentLikeRepository).save(any(PostCommentLike.class));
        verify(commentRepository).changeLikeCount(10L, 1);
    }

    @Test
    @DisplayName("이미 좋아요한 댓글을 다시 눌러도 중복 저장하지 않는다")
    void likeIsIdempotent() {
        PostComment comment = new PostComment(mock(Post.class), null, mock(User.class), "c");
        when(commentRepository.findByIdAndPostIdForUpdate(10L, 1L)).thenReturn(Optional.of(comment));
        when(commentRepository.findByIdAndPostId(10L, 1L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.existsByCommentIdAndUserId(10L, 2L)).thenReturn(true);

        var response = service.like(1L, 10L, 2L);

        assertTrue(response.active());
        verify(commentLikeRepository, never()).save(any());
        verify(commentRepository, never()).changeLikeCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("누르지 않은 댓글의 좋아요를 취소해도 카운트가 줄지 않는다")
    void unlikeWithoutLikeDoesNothing() {
        PostComment comment = new PostComment(mock(Post.class), null, mock(User.class), "c");
        when(commentRepository.findByIdAndPostIdForUpdate(10L, 1L)).thenReturn(Optional.of(comment));
        when(commentRepository.findByIdAndPostId(10L, 1L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.findByCommentIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        var response = service.unlike(1L, 10L, 2L);

        assertFalse(response.active());
        verify(commentRepository, never()).changeLikeCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("비로그인 조회는 좋아요 여부를 확인하지 않고 liked=false로 준다")
    void guestListingSkipsLikeLookup() {
        when(postRepository.existsById(1L)).thenReturn(true);
        PostComment comment = new PostComment(mock(Post.class), null, mock(User.class), "c");
        when(commentRepository.findByPostIdAndParentIsNull(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comment)));

        var response = service.getComments(1L, null, 0, 20);

        assertFalse(response.comments().get(0).liked());
        verifyNoInteractions(commentLikeRepository);
    }

    @Test
    @DisplayName("좋아요 토글은 댓글 행을 잠근 뒤 처리한다(하트 연타 시 중복 INSERT 방지)")
    void likeLocksCommentRow() {
        PostComment comment = new PostComment(mock(Post.class), null, mock(User.class), "c");
        when(commentRepository.findByIdAndPostIdForUpdate(10L, 1L)).thenReturn(Optional.of(comment));
        when(commentRepository.findByIdAndPostId(10L, 1L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.existsByCommentIdAndUserId(10L, 2L)).thenReturn(false);

        service.like(1L, 10L, 2L);
        service.unlike(1L, 10L, 2L);

        // 잠그지 않는 조회로 되돌아가면 동시 요청이 유니크 제약을 위반해 500이 난다.
        verify(commentRepository, times(2)).findByIdAndPostIdForUpdate(10L, 1L);
    }
}
