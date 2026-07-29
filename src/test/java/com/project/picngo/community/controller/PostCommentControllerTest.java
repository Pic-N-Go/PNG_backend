package com.project.picngo.community.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.community.dto.CommentResponse;
import com.project.picngo.community.dto.CommentUpdateRequest;
import com.project.picngo.community.service.PostCommentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCommentControllerTest {

    @Mock
    private PostCommentService commentService;

    @Mock
    private CustomUserDetails userDetails;

    @InjectMocks
    private PostCommentController controller;

    @Test
    @DisplayName("댓글 수정 요청은 경로와 로그인 사용자 정보를 서비스에 전달한다")
    void updateCommentDelegatesToService() {
        CommentUpdateRequest request = new CommentUpdateRequest("updated");
        CommentResponse expected = mock(CommentResponse.class);
        when(userDetails.getId()).thenReturn(9L);
        when(commentService.updateComment(1L, 10L, 9L, request)).thenReturn(expected);

        var response = controller.updateComment(userDetails, 1L, 10L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(commentService).updateComment(1L, 10L, 9L, request);
    }

    @Test
    @DisplayName("댓글 삭제 요청은 경로와 로그인 사용자 정보를 서비스에 전달하고 204를 반환한다")
    void deleteCommentDelegatesToService() {
        when(userDetails.getId()).thenReturn(9L);

        var response = controller.deleteComment(userDetails, 1L, 10L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(commentService).deleteComment(1L, 10L, 9L);
    }
}
