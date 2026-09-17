package com.project.picngo.community.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.CommunityErrorCode;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.community.domain.Post;
import com.project.picngo.community.repository.PostBookmarkRepository;
import com.project.picngo.community.repository.PostCommentLikeRepository;
import com.project.picngo.community.repository.PostCommentRepository;
import com.project.picngo.community.repository.PostImageRepository;
import com.project.picngo.community.repository.PostLikeRepository;
import com.project.picngo.community.repository.PostRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostCommentRepository commentRepository;

    @Mock
    private PostCommentLikeRepository commentLikeRepository;

    @Mock
    private PostLikeRepository likeRepository;

    @Mock
    private PostBookmarkRepository bookmarkRepository;

    @Mock
    private PostImageRepository imageRepository;

    @Mock
    private ImageStorageService imageStorageService;

    @InjectMocks
    private AdminPostService adminPostService;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("관리자 게시글 삭제는 연관 데이터를 순서대로 삭제하고 이미지를 정리한다")
    void deletePostByAdminDeletesRelationsAndImages() {
        Post post = mock(Post.class);
        when(postRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(post));
        when(imageRepository.findObjectKeysByPostId(30L)).thenReturn(List.of("image-1", "image-2"));

        adminPostService.deletePostByAdmin(30L);

        InOrder order = inOrder(
                commentLikeRepository,
                commentRepository,
                likeRepository,
                bookmarkRepository,
                imageRepository,
                postRepository
        );
        order.verify(commentLikeRepository).deleteAllByPostId(30L);
        order.verify(commentRepository).deleteAllByPostId(30L);
        order.verify(likeRepository).deleteAllByPostId(30L);
        order.verify(bookmarkRepository).deleteAllByPostId(30L);
        order.verify(imageRepository).deleteAllByPostId(30L);
        order.verify(postRepository).delete(post);
        order.verify(postRepository).flush();
        verify(imageStorageService).delete("image-1");
        verify(imageStorageService).delete("image-2");
    }

    @Test
    @DisplayName("존재하지 않는 게시글은 관리자가 삭제할 수 없다")
    void deletePostByAdminThrowsWhenPostDoesNotExist() {
        when(postRepository.findByIdForUpdate(30L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminPostService.deletePostByAdmin(30L))
                .isInstanceOf(CustomException.class)
                .hasMessage(CommunityErrorCode.POST_NOT_FOUND.getMessage());

        verify(postRepository, never()).delete(any());
    }

    @Test
    @DisplayName("게시글 이미지는 트랜잭션 커밋 후 삭제한다")
    void deletePostImagesAfterCommit() {
        Post post = mock(Post.class);
        when(postRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(post));
        when(imageRepository.findObjectKeysByPostId(30L)).thenReturn(List.of("image-1"));
        TransactionSynchronizationManager.initSynchronization();

        adminPostService.deletePostByAdmin(30L);

        verify(imageStorageService, never()).delete("image-1");

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(imageStorageService).delete("image-1");
    }
}
