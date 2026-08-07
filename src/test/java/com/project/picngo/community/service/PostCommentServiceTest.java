package com.project.picngo.community.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.CommunityErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.community.domain.Post;
import com.project.picngo.community.domain.PostComment;
import com.project.picngo.community.dto.CommentCreateRequest;
import com.project.picngo.community.dto.CommentUpdateRequest;
import com.project.picngo.community.repository.PostRepository;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostCommentServiceTest {

    @Mock PostRepository postRepository;
    @Mock PostCommentRepository commentRepository;
    @Mock UserRepository userRepository;

    @InjectMocks PostCommentService service;

    @Test
    @DisplayName("존재하지 않는 게시글의 댓글 목록을 조회하면 POST_NOT_FOUND 예외를 반환한다")
    void commentsOfMissingPostAreRejected() {
        when(postRepository.existsById(404L)).thenReturn(false);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.getComments(404L, 0, 20)
        );

        assertEquals(CommunityErrorCode.POST_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(commentRepository);
    }

    @Test
    @DisplayName("댓글 목록의 음수 페이지와 과도한 페이지 크기를 안전한 범위로 보정한다")
    void commentPageAndSizeAreNormalized() {
        when(postRepository.existsById(1L)).thenReturn(true);
        when(commentRepository.findByPostId(eq(1L), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));

        service.getComments(1L, -1, 1000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findByPostId(eq(1L), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(100, pageableCaptor.getValue().getPageSize());
    }

    @Test
    @DisplayName("존재하지 않는 게시글에는 댓글을 작성할 수 없다")
    void commentCannotBeCreatedForMissingPost() {
        when(postRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.createComment(404L, 2L, new CommentCreateRequest("comment"))
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
                () -> service.createComment(1L, 404L, new CommentCreateRequest("comment"))
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

        var response = service.createComment(1L, 2L, new CommentCreateRequest("  hello  "));

        assertEquals("hello", response.content());
        verify(commentRepository).save(any(PostComment.class));
        verify(postRepository).incrementCommentCount(1L);
    }

    @Test
    @DisplayName("댓글 작성자는 댓글 내용을 수정할 수 있고 앞뒤 공백은 제거된다")
    void commentAuthorCanUpdateContent() {
        User author = mock(User.class);
        when(author.getId()).thenReturn(2L);
        PostComment comment = new PostComment(mock(Post.class), author, "before");
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
        PostComment comment = new PostComment(mock(Post.class), author, "before");
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
        PostComment comment = new PostComment(mock(Post.class), author, "comment");
        when(commentRepository.findByIdAndPostId(10L, 1L))
                .thenReturn(Optional.of(comment));

        service.deleteComment(1L, 10L, 2L);

        verify(commentRepository).delete(comment);
        verify(postRepository).decrementCommentCount(1L);
    }

    @Test
    @DisplayName("댓글 작성자가 아니면 댓글을 삭제할 수 없다")
    void deleteCommentRequiresAuthor() {
        User author = mock(User.class);
        when(author.getId()).thenReturn(2L);
        PostComment comment = new PostComment(mock(Post.class), author, "comment");
        when(commentRepository.findByIdAndPostId(10L, 1L))
                .thenReturn(Optional.of(comment));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.deleteComment(1L, 10L, 9L)
        );

        assertEquals(CommunityErrorCode.COMMENT_FORBIDDEN, exception.getErrorCode());
        verify(commentRepository, never()).delete(any());
        verify(postRepository, never()).decrementCommentCount(any());
    }
}
