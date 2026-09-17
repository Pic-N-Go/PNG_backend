package com.project.picngo.report.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.CommunityErrorCode;
import com.project.picngo.common.exception.code.ReportErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.community.domain.Post;
import com.project.picngo.community.repository.PostRepository;
import com.project.picngo.report.domain.Report;
import com.project.picngo.report.domain.ReportReason;
import com.project.picngo.report.domain.ReportStatus;
import com.project.picngo.report.domain.ReportTargetType;
import com.project.picngo.report.dto.ReportCreateRequest;
import com.project.picngo.report.dto.ReportCreateResponse;
import com.project.picngo.report.repository.ReportRepository;
import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.repository.ReviewRepository;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReportService reportService;

    @Test
    @DisplayName("게시글 신고를 접수하면 대상과 원문을 저장하고 PENDING 상태를 반환한다")
    void reportPostSuccess() {
        User reporter = user(1L);
        User author = user(2L);
        Post post = post(author, "신고 당시 게시글 본문");
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 13, 12, 0);

        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(reportRepository.existsByReporter_IdAndTargetTypeAndTargetId(1L, ReportTargetType.POST, 10L))
                .thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 100L);
            ReflectionTestUtils.setField(report, "createdAt", createdAt);
            return report;
        });

        ReportCreateResponse response = reportService.reportPost(
                1L,
                10L,
                new ReportCreateRequest(ReportReason.SPAM, "반복 광고입니다.")
        );

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report savedReport = captor.getValue();

        assertThat(savedReport.getTargetType()).isEqualTo(ReportTargetType.POST);
        assertThat(savedReport.getTargetId()).isEqualTo(10L);
        assertThat(savedReport.getReporter()).isSameAs(reporter);
        assertThat(savedReport.getReportedUser()).isSameAs(author);
        assertThat(savedReport.getReason()).isEqualTo(ReportReason.SPAM);
        assertThat(savedReport.getDetail()).isEqualTo("반복 광고입니다.");
        assertThat(savedReport.getTargetContentSnapshot()).isEqualTo("신고 당시 게시글 본문");
        assertThat(response.reportId()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(ReportStatus.PENDING);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("공백으로만 입력된 상세 내용은 null로 저장한다")
    void blankDetailIsStoredAsNull() {
        User reporter = user(1L);
        User author = user(2L);
        Post post = post(author, "본문");

        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(reportRepository.existsByReporter_IdAndTargetTypeAndTargetId(1L, ReportTargetType.POST, 10L))
                .thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 100L);
            ReflectionTestUtils.setField(report, "createdAt", LocalDateTime.now());
            return report;
        });

        reportService.reportPost(1L, 10L, new ReportCreateRequest(ReportReason.ETC, "   "));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).isNull();
    }

    @Test
    @DisplayName("신고자를 찾을 수 없으면 신고를 저장하지 않는다")
    void reporterNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> reportService.reportPost(1L, 10L, request())
        );

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verify(postRepository, never()).findById(any());
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("게시글을 찾을 수 없으면 신고를 저장하지 않는다")
    void postNotFound() {
        User reporter = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(postRepository.findById(10L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> reportService.reportPost(1L, 10L, request())
        );

        assertEquals(CommunityErrorCode.POST_NOT_FOUND, exception.getErrorCode());
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("자신이 작성한 게시글은 신고할 수 없다")
    void selfReportIsRejected() {
        User reporter = user(1L);
        Post post = post(reporter, "본문");
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> reportService.reportPost(1L, 10L, request())
        );

        assertEquals(ReportErrorCode.SELF_REPORT_NOT_ALLOWED, exception.getErrorCode());
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 사용자가 같은 게시글을 다시 신고하면 거부한다")
    void duplicateReportIsRejected() {
        User reporter = user(1L);
        Post post = post(user(2L), "본문");
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(reportRepository.existsByReporter_IdAndTargetTypeAndTargetId(1L, ReportTargetType.POST, 10L))
                .thenReturn(true);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> reportService.reportPost(1L, 10L, request())
        );

        assertEquals(ReportErrorCode.DUPLICATE_REPORT, exception.getErrorCode());
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("리뷰 신고를 접수하면 별점과 본문 스냅샷을 저장한다")
    void reportReviewSuccess() {
        User reporter = user(1L);
        User reviewAuthor = user(2L);
        Review review = review(2L, 1, "신고 당시 리뷰 본문");
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 14, 12, 0);

        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reviewRepository.findById(20L)).thenReturn(Optional.of(review));
        when(reportRepository.existsByReporter_IdAndTargetTypeAndTargetId(1L, ReportTargetType.REVIEW, 20L))
                .thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(reviewAuthor));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 200L);
            ReflectionTestUtils.setField(report, "createdAt", createdAt);
            return report;
        });

        ReportCreateResponse response = reportService.reportReview(
                1L,
                20L,
                new ReportCreateRequest(ReportReason.ABUSE, "욕설이 포함되어 있습니다.")
        );

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report savedReport = captor.getValue();

        assertThat(savedReport.getTargetType()).isEqualTo(ReportTargetType.REVIEW);
        assertThat(savedReport.getTargetId()).isEqualTo(20L);
        assertThat(savedReport.getReporter()).isSameAs(reporter);
        assertThat(savedReport.getReportedUser()).isSameAs(reviewAuthor);
        assertThat(savedReport.getReason()).isEqualTo(ReportReason.ABUSE);
        assertThat(savedReport.getTargetContentSnapshot())
                .isEqualTo("별점: 1" + System.lineSeparator() + "내용: 신고 당시 리뷰 본문");
        assertThat(response.reportId()).isEqualTo(200L);
        assertThat(response.status()).isEqualTo(ReportStatus.PENDING);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("존재하지 않는 리뷰는 신고할 수 없다")
    void reviewNotFound() {
        User reporter = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reviewRepository.findById(20L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> reportService.reportReview(1L, 20L, request())
        );

        assertEquals(com.project.picngo.common.exception.code.ReviewErrorCode.REVIEW_NOT_FOUND,
                exception.getErrorCode());
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("자신이 작성한 리뷰는 신고할 수 없다")
    void selfReviewReportIsRejected() {
        User reporter = user(1L);
        Review review = review(1L, 5, "본문");
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reviewRepository.findById(20L)).thenReturn(Optional.of(review));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> reportService.reportReview(1L, 20L, request())
        );

        assertEquals(ReportErrorCode.SELF_REPORT_NOT_ALLOWED, exception.getErrorCode());
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 사용자가 같은 리뷰를 다시 신고하면 거부한다")
    void duplicateReviewReportIsRejected() {
        User reporter = user(1L);
        Review review = review(2L, 5, "본문");
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reviewRepository.findById(20L)).thenReturn(Optional.of(review));
        when(reportRepository.existsByReporter_IdAndTargetTypeAndTargetId(1L, ReportTargetType.REVIEW, 20L))
                .thenReturn(true);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> reportService.reportReview(1L, 20L, request())
        );

        assertEquals(ReportErrorCode.DUPLICATE_REPORT, exception.getErrorCode());
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("리뷰 작성자를 찾을 수 없으면 리뷰 신고를 저장하지 않는다")
    void reviewAuthorNotFound() {
        User reporter = user(1L);
        Review review = review(2L, 5, "리뷰 본문");
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reviewRepository.findById(20L)).thenReturn(Optional.of(review));
        when(reportRepository.existsByReporter_IdAndTargetTypeAndTargetId(
                1L,
                ReportTargetType.REVIEW,
                20L
        )).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> reportService.reportReview(1L, 20L, request())
        );

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verify(reportRepository, never()).save(any());
    }

    private ReportCreateRequest request() {
        return new ReportCreateRequest(ReportReason.SPAM, "상세 내용");
    }

    private User user(Long id) {
        User user = org.mockito.Mockito.mock(User.class);
        org.mockito.Mockito.lenient().when(user.getId()).thenReturn(id);
        return user;
    }

    private Post post(User author, String content) {
        Post post = org.mockito.Mockito.mock(Post.class);
        when(post.getAuthor()).thenReturn(author);
        org.mockito.Mockito.lenient().when(post.getContent()).thenReturn(content);
        return post;
    }

    private Review review(Long userId, Integer rating, String content) {
        Review review = org.mockito.Mockito.mock(Review.class);
        when(review.getUserId()).thenReturn(userId);
        org.mockito.Mockito.lenient().when(review.getRating()).thenReturn(rating);
        org.mockito.Mockito.lenient().when(review.getContent()).thenReturn(content);
        return review;
    }
}
