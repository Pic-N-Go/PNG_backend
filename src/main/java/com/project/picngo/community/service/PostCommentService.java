package com.project.picngo.community.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.CommunityErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.community.domain.Post;
import com.project.picngo.community.domain.PostComment;
import com.project.picngo.community.dto.CommentCreateRequest;
import com.project.picngo.community.dto.CommentPageResponse;
import com.project.picngo.community.dto.CommentResponse;
import com.project.picngo.community.dto.CommentUpdateRequest;
import com.project.picngo.community.dto.PostAuthorResponse;
import com.project.picngo.community.repository.PostRepository;
import com.project.picngo.community.repository.PostCommentRepository;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final UserRepository userRepository;

    public CommentPageResponse getComments(Long postId, int page, int size) {
        ensurePostExists(postId);
        int normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizedSize, Sort.by(Sort.Direction.ASC, "createdAt"));

        Page<PostComment> result = commentRepository.findByPostId(postId, pageable);

        List<CommentResponse> comments = result.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new CommentPageResponse(
                comments,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize(),
                result.hasNext()
        );
    }

    @Transactional
    public CommentResponse createComment(Long postId, Long userId, CommentCreateRequest request) {
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        PostComment comment = commentRepository.save(
                new PostComment(post, author, request.content().trim())
        );
        postRepository.incrementCommentCount(postId);

        return toResponse(comment);
    }

    @Transactional
    public CommentResponse updateComment(
            Long postId,
            Long commentId,
            Long userId,
            CommentUpdateRequest request
    ) {
        PostComment comment = findComment(postId, commentId);
        validateCommentAuthor(comment, userId);
        comment.updateContent(request.content().trim());
        return toResponse(comment);
    }

    @Transactional
    public void deleteComment(Long postId, Long commentId, Long userId) {
        PostComment comment = findComment(postId, commentId);
        validateCommentAuthor(comment, userId);

        commentRepository.delete(comment);
        postRepository.decrementCommentCount(postId);
    }

    private void ensurePostExists(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new CustomException(CommunityErrorCode.POST_NOT_FOUND);
        }
    }

    private PostComment findComment(Long postId, Long commentId) {
        return commentRepository.findByIdAndPostId(commentId, postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.COMMENT_NOT_FOUND));
    }

    private void validateCommentAuthor(PostComment comment, Long userId) {
        if (userId == null || !Objects.equals(comment.getAuthor().getId(), userId)) {
            throw new CustomException(CommunityErrorCode.COMMENT_FORBIDDEN);
        }
    }

    private CommentResponse toResponse(PostComment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                PostAuthorResponse.from(comment.getAuthor()),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
