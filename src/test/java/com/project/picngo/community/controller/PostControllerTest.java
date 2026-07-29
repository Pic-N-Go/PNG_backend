package com.project.picngo.community.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.community.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    @Mock
    private PostService postService;

    @Mock
    private CustomUserDetails userDetails;

    @InjectMocks
    private PostController controller;

    @Test
    @DisplayName("게시글 삭제 요청은 로그인 사용자 ID를 서비스에 전달하고 204를 반환한다")
    void deletePostDelegatesToServiceAndReturnsNoContent() {
        when(userDetails.getId()).thenReturn(9L);

        var response = controller.deletePost(userDetails, 1L);

        verify(postService).deletePost(1L, 9L);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }
}
