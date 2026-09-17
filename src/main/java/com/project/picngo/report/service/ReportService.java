package com.project.picngo.report.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.CommunityErrorCode;
import com.project.picngo.common.exception.code.ReportErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.community.domain.Post;
import com.project.picngo.community.repository.PostRepository;
import com.project.picngo.report.domain.Report;
import com.project.picngo.report.domain.ReportTargetType;
import com.project.picngo.report.dto.ReportCreateRequest;
import com.project.picngo.report.dto.ReportCreateResponse;
import com.project.picngo.report.repository.ReportRepository;
import com.project.picngo.common.exception.code.ReviewErrorCode;
import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.repository.ReviewRepository;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    @Transactional
    public ReportCreateResponse reportPost(Long reporterId, Long postId, ReportCreateRequest request) {

        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));

        if (post.getAuthor().getId().equals(reporterId)) {
            throw new CustomException(ReportErrorCode.SELF_REPORT_NOT_ALLOWED);
        }

        if (reportRepository.existsByReporter_IdAndTargetTypeAndTargetId(reporterId, ReportTargetType.POST, postId)) {
            throw new CustomException(ReportErrorCode.DUPLICATE_REPORT);
        }

        Report report = Report.create(
                ReportTargetType.POST,
                postId,
                reporter,
                post.getAuthor(),
                request.reason(),
                request.detail(),
                post.getContent()
        );

        Report savedReport = reportRepository.save(report);

        return ReportCreateResponse.from(savedReport);
    }

    @Transactional
    public ReportCreateResponse reportReview(Long reporterId, Long reviewId, ReportCreateRequest request) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ReviewErrorCode.REVIEW_NOT_FOUND));

        if (review.getUserId().equals(reporterId)) {
            throw new CustomException(ReportErrorCode.SELF_REPORT_NOT_ALLOWED);
        }

        if (reportRepository.existsByReporter_IdAndTargetTypeAndTargetId(
                reporterId,
                ReportTargetType.REVIEW,
                reviewId
        )) {
            throw new CustomException(ReportErrorCode.DUPLICATE_REPORT);
        }

        User reviewAuthor = userRepository.findById(review.getUserId())
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        String targetContentSnapshot = String.format(
                "별점: %d%n내용: %s",
                review.getRating(),
                review.getContent()
        );

        Report report = Report.create(
                ReportTargetType.REVIEW,
                reviewId,
                reporter,
                reviewAuthor,
                request.reason(),
                request.detail(),
                targetContentSnapshot
        );

        Report savedReport = reportRepository.save(report);

        return ReportCreateResponse.from(savedReport);
    }
}
