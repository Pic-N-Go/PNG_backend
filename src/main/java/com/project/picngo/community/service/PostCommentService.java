package com.project.picngo.community.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.common.exception.code.CommunityErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.community.domain.Post;
import com.project.picngo.community.domain.PostComment;
import com.project.picngo.community.domain.PostCommentLike;
import com.project.picngo.community.dto.CommentCreateRequest;
import com.project.picngo.community.dto.CommentPageResponse;
import com.project.picngo.community.dto.CommentResponse;
import com.project.picngo.community.dto.CommentUpdateRequest;
import com.project.picngo.community.dto.PostAuthorResponse;
import com.project.picngo.community.dto.ReactionResponse;
import com.project.picngo.community.repository.PostCommentLikeRepository;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommentService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final Sort OLDEST_FIRST = Sort.by(Sort.Direction.ASC, "createdAt");
    private static final Sort TOP_LEVEL_SORT = Sort.by(
            Sort.Order.desc("likeCount"),
            Sort.Order.asc("createdAt"),
            Sort.Order.asc("id")
    );

    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final PostCommentLikeRepository commentLikeRepository;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;

    /**
     * 최상위 댓글만 준다. 답글은 getReplies로 따로 받아간다("답글 N개 보기").
     *
     * 좋아요가 많은 댓글을 위로 올린다. 같은 좋아요 수면 오래된 것부터 - createdAt까지 같은
     * 경우가 있어 id를 마지막 기준으로 둔다(정렬이 흔들리면 페이지 경계에서 댓글이 중복되거나 빠진다).
     */
    public CommentPageResponse getComments(Long postId, Long userId, int page, int size) {
        ensurePostExists(postId);
        Page<PostComment> result = commentRepository.findByPostIdAndParentIsNull(postId, pageable(page, size, TOP_LEVEL_SORT));
        return toPageResponse(result, userId);
    }

    /** 답글은 대화 흐름이라 좋아요와 무관하게 시간순으로 둔다. */
    public CommentPageResponse getReplies(Long postId, Long commentId, Long userId, int page, int size) {
        PostComment parent = findComment(postId, commentId);
        Page<PostComment> result = commentRepository.findByParentId(parent.getId(), pageable(page, size, OLDEST_FIRST));
        return toPageResponse(result, userId);
    }

    @Transactional
    public CommentResponse createComment(Long postId, Long userId, CommentCreateRequest request) {
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        PostComment parent = resolveParent(postId, request.parentId());

        PostComment comment = commentRepository.save(
                new PostComment(post, parent, author, request.content().trim())
        );
        // 답글도 게시글 댓글 수에 포함한다 - 화면의 "댓글 N개"는 답글까지 합친 수가 자연스럽다.
        postRepository.incrementCommentCount(postId);
        if (parent != null) {
            commentRepository.changeReplyCount(parent.getId(), 1);
        }

        return toResponse(comment, false);
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
        return toResponse(comment, isLiked(commentId, userId));
    }

    @Transactional
    public void deleteComment(Long postId, Long commentId, Long userId) {
        PostComment comment = findComment(postId, commentId);
        validateCommentAuthor(comment, userId);

        // 부모를 지우면 답글도 함께 사라진다. 지운 개수만큼 게시글 댓글 수도 함께 줄여야 한다.
        List<Long> replyIds = commentRepository.findByParentId(comment.getId(), Pageable.unpaged())
                .getContent().stream().map(PostComment::getId).toList();

        // 답글과 자기 자신의 좋아요를 한 번에 지운다(쿼리 1회). List.of는 null을 거부하므로 ArrayList를 쓴다.
        List<Long> likeTargetIds = new ArrayList<>(replyIds);
        likeTargetIds.add(comment.getId());
        commentLikeRepository.deleteAllByCommentIdIn(likeTargetIds);

        if (!replyIds.isEmpty()) {
            commentRepository.deleteAllByParentId(comment.getId());
        }

        if (comment.isReply()) {
            commentRepository.changeReplyCount(comment.getParent().getId(), -1);
        }
        commentRepository.delete(comment);
        postRepository.changeCommentCount(postId, -(replyIds.size() + 1));
    }

    @Transactional
    public ReactionResponse like(Long postId, Long commentId, Long userId) {
        PostComment comment = findCommentForUpdate(postId, commentId);
        if (!commentLikeRepository.existsByCommentIdAndUserId(commentId, userId)) {
            commentLikeRepository.save(new PostCommentLike(comment, userId));
            commentRepository.changeLikeCount(commentId, 1);
        }
        return new ReactionResponse(true, currentLikeCount(postId, commentId));
    }

    @Transactional
    public ReactionResponse unlike(Long postId, Long commentId, Long userId) {
        findCommentForUpdate(postId, commentId);
        commentLikeRepository.findByCommentIdAndUserId(commentId, userId).ifPresent(like -> {
            commentLikeRepository.delete(like);
            commentRepository.changeLikeCount(commentId, -1);
        });
        return new ReactionResponse(false, currentLikeCount(postId, commentId));
    }

    private Pageable pageable(int page, int size, Sort sort) {
        int normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return PageRequest.of(Math.max(page, 0), normalizedSize, sort);
    }

    /**
     * 답글에 답글을 달면 그 답글의 부모로 붙인다(깊이 1단계 고정).
     * 계층이 깊어지면 화면도 조회도 복잡해지는데, 인스타그램도 같은 방식이라 사용자 기대와도 맞는다.
     */
    private PostComment resolveParent(Long postId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        PostComment parent = findComment(postId, parentId);
        return parent.isReply() ? parent.getParent() : parent;
    }

    private CommentPageResponse toPageResponse(Page<PostComment> result, Long userId) {
        List<PostComment> content = result.getContent();
        Set<Long> likedIds = likedIdsOf(content, userId);

        List<CommentResponse> comments = content.stream()
                .map(comment -> toResponse(comment, likedIds.contains(comment.getId())))
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

    /**
     * 한 페이지분을 한 번의 쿼리로 판정한다(댓글마다 조회하면 N+1).
     * Set.of/copyOf는 contains(null)에서 NPE를 던지므로 HashSet을 쓴다.
     */
    private Set<Long> likedIdsOf(List<PostComment> comments, Long userId) {
        if (userId == null || comments.isEmpty()) {
            return new HashSet<>();
        }
        List<Long> ids = comments.stream().map(PostComment::getId).toList();
        return new HashSet<>(commentLikeRepository.findLikedCommentIds(userId, ids));
    }

    private boolean isLiked(Long commentId, Long userId) {
        return userId != null && commentLikeRepository.existsByCommentIdAndUserId(commentId, userId);
    }

    private long currentLikeCount(Long postId, Long commentId) {
        return findComment(postId, commentId).getLikeCount();
    }

    private void ensurePostExists(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new CustomException(CommunityErrorCode.POST_NOT_FOUND);
        }
    }

    /** 좋아요 토글 전용. 행을 잠가 같은 사용자의 동시 요청이 중복 INSERT로 500을 내는 걸 막는다. */
    private PostComment findCommentForUpdate(Long postId, Long commentId) {
        return commentRepository.findByIdAndPostIdForUpdate(commentId, postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.COMMENT_NOT_FOUND));
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

    private CommentResponse toResponse(PostComment comment, boolean liked) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                PostAuthorResponse.from(comment.getAuthor(), imageStorageService.getPresignedUrl(comment.getAuthor().getDisplayProfileImage())),
                comment.isReply() ? comment.getParent().getId() : null,
                comment.getReplyCount(),
                comment.getLikeCount(),
                liked,
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
