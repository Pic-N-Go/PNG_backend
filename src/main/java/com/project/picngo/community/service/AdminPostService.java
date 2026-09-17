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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostService {

    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final PostCommentLikeRepository commentLikeRepository;
    private final PostLikeRepository likeRepository;
    private final PostBookmarkRepository bookmarkRepository;
    private final PostImageRepository imageRepository;
    private final ImageStorageService imageStorageService;

    @Transactional
    public void deletePostByAdmin(Long postId) {
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));

        List<String> imageObjectKeys = imageRepository.findObjectKeysByPostId(postId);

        commentLikeRepository.deleteAllByPostId(postId);
        commentRepository.deleteAllByPostId(postId);
        likeRepository.deleteAllByPostId(postId);
        bookmarkRepository.deleteAllByPostId(postId);
        imageRepository.deleteAllByPostId(postId);
        postRepository.delete(post);
        postRepository.flush();

        deleteImagesAfterCommit(imageObjectKeys);
    }

    private void deleteImagesAfterCommit(List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteImages(objectKeys);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteImages(objectKeys);
                    }
                }
        );
    }

    private void deleteImages(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            try {
                imageStorageService.delete(objectKey);
            } catch (RuntimeException exception) {
                log.warn("관리자 게시글 삭제 후 S3 이미지 정리에 실패했습니다. key={}", objectKey, exception);
            }
        }
    }
}
