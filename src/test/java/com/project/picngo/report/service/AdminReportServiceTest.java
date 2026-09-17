package com.project.picngo.report.service;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ReportErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.community.service.AdminPostService;
import com.project.picngo.report.domain.Report;
import com.project.picngo.report.domain.ReportReason;
import com.project.picngo.report.domain.ReportStatus;
import com.project.picngo.report.domain.ReportTargetType;
import com.project.picngo.report.dto.AdminReportDetailResponse;
import com.project.picngo.report.dto.AdminReportListResponse;
import com.project.picngo.report.dto.AdminReportProcessRequest;
import com.project.picngo.report.dto.AdminReportProcessResponse;
import com.project.picngo.report.dto.AdminReportTargetDeleteRequest;
import com.project.picngo.report.repository.ReportRepository;
import com.project.picngo.spot.service.AdminReviewService;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @Mock
    private AdminPostService adminPostService;

    @Mock
    private AdminReviewService adminReviewService;

    @Mock
    private AdminReportNotificationService notificationService;

    @InjectMocks
    private AdminReportService adminReportService;

    @BeforeEach
    void setUpTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("전체 신고는 최신순으로 페이지당 기본 10개를 조회한다")
    void getAllReportsUsesDefaultPageSizeAndLatestSort() {
        when(reportRepository.findAllForAdmin(any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(0);
            return new PageImpl<>(List.of(), pageable, 0);
        });

        Page<AdminReportListResponse> result = adminReportService.getAllReports(0, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(reportRepository).findAllForAdmin(captor.capture());
        Pageable pageable = captor.getValue();

        assertThat(result).isEmpty();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    @DisplayName("전체 신고 목록은 게시글과 리뷰 신고를 함께 반환한다")
    void getAllReportsReturnsPostAndReviewReportsTogether() {
        Report postReport = report(100L, 10L, ReportTargetType.POST, 30L);
        Report reviewReport = report(101L, 11L, ReportTargetType.REVIEW, 40L);
        when(reportRepository.findAllForAdmin(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reviewReport, postReport)));

        Page<AdminReportListResponse> result = adminReportService.getAllReports(0, 10);

        assertThat(result.getContent())
                .extracting(AdminReportListResponse::targetType)
                .containsExactly(ReportTargetType.REVIEW, ReportTargetType.POST);
    }

    @Test
    @DisplayName("대기 신고 목록은 대상 타입과 관계없이 조회한다")
    void getPendingReportsReturnsAllTargetTypes() {
        Report postReport = report(100L, 10L, ReportTargetType.POST, 30L);
        Report reviewReport = report(101L, 11L, ReportTargetType.REVIEW, 40L);
        when(reportRepository.findByStatus(eq(ReportStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(postReport, reviewReport)));

        Page<AdminReportListResponse> result = adminReportService.getPendingReports(0, 10);

        assertThat(result.getContent())
                .extracting(AdminReportListResponse::targetType)
                .containsExactly(ReportTargetType.POST, ReportTargetType.REVIEW);
    }

    @Test
    @DisplayName("상태별 목록은 PENDING, RESOLVED, DISMISSED 상태를 각각 조회한다")
    void getReportsByStatusUsesExpectedStatus() {
        when(reportRepository.findByStatus(any(ReportStatus.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    return new PageImpl<Report>(List.of(), pageable, 0);
                });

        adminReportService.getPendingReports(0, 10);
        adminReportService.getResolvedReports(0, 10);
        adminReportService.getDismissedReports(0, 10);

        verify(reportRepository).findByStatus(eq(ReportStatus.PENDING), any(Pageable.class));
        verify(reportRepository).findByStatus(eq(ReportStatus.RESOLVED), any(Pageable.class));
        verify(reportRepository).findByStatus(eq(ReportStatus.DISMISSED), any(Pageable.class));
    }

    @Test
    @DisplayName("신고 상세 조회 시 신고 내용과 원문 스냅샷을 반환한다")
    void getReportReturnsDetail() {
        Report report = report(100L);
        when(reportRepository.findDetailById(100L)).thenReturn(Optional.of(report));

        AdminReportDetailResponse response = adminReportService.getReport(100L);

        assertThat(response.reportId()).isEqualTo(100L);
        assertThat(response.reason()).isEqualTo(ReportReason.SPAM);
        assertThat(response.detail()).isEqualTo("신고 상세 내용");
        assertThat(response.targetContentSnapshot()).isEqualTo("신고 당시 원문");
        assertThat(response.status()).isEqualTo(ReportStatus.PENDING);
        assertThat(response.handledById()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 신고 상세를 조회하면 예외가 발생한다")
    void reportDetailNotFound() {
        when(reportRepository.findDetailById(100L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> adminReportService.getReport(100L)
        );

        assertEquals(ReportErrorCode.REPORT_NOT_FOUND, exception.getErrorCode());
    }

    @ParameterizedTest
    @EnumSource(value = ReportStatus.class, names = {"RESOLVED", "DISMISSED"})
    @DisplayName("관리자는 신고를 조치 완료 또는 기각 상태로 처리할 수 있다")
    void processReportSuccess(ReportStatus status) {
        User admin = user(1L, "관리자");
        Report report = report(100L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(reportRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(report));

        AdminReportProcessResponse response = adminReportService.processReport(
                1L,
                100L,
                new AdminReportProcessRequest(status, "처리 메모")
        );

        assertThat(response.status()).isEqualTo(status);
        assertThat(response.handledById()).isEqualTo(1L);
        assertThat(response.handledAt()).isNotNull();
        assertThat(response.resolutionNote()).isEqualTo("처리 메모");
        triggerAfterCommit();
        verify(adminAuditLogService).record(
                eq(1L),
                eq(AdminActionType.REPORT_PROCESS),
                eq("REPORT"),
                eq("100"),
                anyString(),
                isNull()
        );
    }

    @Test
    @DisplayName("관리자 처리 상태로 PENDING을 요청하면 거부한다")
    void pendingStatusIsRejected() {
        CustomException exception = assertThrows(
                CustomException.class,
                () -> adminReportService.processReport(
                        1L,
                        100L,
                        new AdminReportProcessRequest(ReportStatus.PENDING, null)
                )
        );

        assertEquals(ReportErrorCode.INVALID_REPORT_STATUS, exception.getErrorCode());
        verify(userRepository, never()).findById(any());
        verify(reportRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("이미 처리된 신고는 다시 처리할 수 없다")
    void processedReportCannotBeProcessedAgain() {
        User firstAdmin = user(2L, "기존 관리자");
        User nextAdmin = user(1L, "다음 관리자");
        Report report = report(100L);
        report.process(firstAdmin, ReportStatus.RESOLVED, "기존 처리");

        when(userRepository.findById(1L)).thenReturn(Optional.of(nextAdmin));
        when(reportRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(report));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> adminReportService.processReport(
                        1L,
                        100L,
                        new AdminReportProcessRequest(ReportStatus.DISMISSED, "재처리")
                )
        );

        assertEquals(ReportErrorCode.REPORT_ALREADY_PROCESSED, exception.getErrorCode());
        verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("신고 게시글을 삭제하면 관련 대기 신고를 모두 완료 처리하고 신고자들에게 알림을 보낸다")
    void deleteReportedPostResolvesRelatedReportsAndNotifiesReporters() {
        User admin = user(1L, "관리자");
        Report firstReport = report(100L, 10L);
        Report secondReport = report(101L, 11L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(reportRepository.findDetailById(100L)).thenReturn(Optional.of(firstReport));
        when(reportRepository.findByTargetAndStatusForUpdate(
                ReportTargetType.POST,
                30L,
                ReportStatus.PENDING
        )).thenReturn(List.of(firstReport, secondReport));

        AdminReportProcessResponse response = adminReportService.deleteReportedTarget(
                1L,
                100L,
                new AdminReportTargetDeleteRequest("관리자 삭제 메모")
        );

        assertThat(response.status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(firstReport.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(secondReport.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(firstReport.getHandledBy()).isSameAs(admin);
        assertThat(secondReport.getHandledBy()).isSameAs(admin);
        assertThat(firstReport.getResolutionNote()).isEqualTo("관리자 삭제 메모");
        assertThat(secondReport.getResolutionNote()).isEqualTo("관리자 삭제 메모");
        verify(adminPostService).deletePostByAdmin(30L);
        verify(notificationService).sendTargetDeletedAfterCommit(
                ReportTargetType.POST,
                List.of(10L, 11L),
                20L,
                100L
        );
        triggerAfterCommit();
        verify(adminAuditLogService).record(
                eq(1L),
                eq(AdminActionType.REPORT_TARGET_DELETE),
                eq("POST"),
                eq("30"),
                anyString(),
                isNull()
        );
    }

    @Test
    @DisplayName("신고 리뷰를 삭제하면 리뷰 삭제 서비스를 호출하고 관련 신고를 완료 처리한다")
    void deleteReportedReviewCallsAdminReviewService() {
        User admin = user(1L, "관리자");
        Report report = report(100L, 10L, ReportTargetType.REVIEW, 40L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(reportRepository.findDetailById(100L)).thenReturn(Optional.of(report));
        when(reportRepository.findByTargetAndStatusForUpdate(
                ReportTargetType.REVIEW,
                40L,
                ReportStatus.PENDING
        )).thenReturn(List.of(report));

        AdminReportProcessResponse response = adminReportService.deleteReportedTarget(
                1L, 100L, new AdminReportTargetDeleteRequest(null)
        );

        assertThat(response.status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(report.getResolutionNote()).isEqualTo("신고된 리뷰 삭제");
        verify(adminReviewService).deleteReviewByAdmin(40L);
        verify(adminPostService, never()).deletePostByAdmin(any());
        verify(notificationService).sendTargetDeletedAfterCommit(
                ReportTargetType.REVIEW,
                List.of(10L),
                20L,
                100L
        );
        triggerAfterCommit();
        verify(adminAuditLogService).record(
                eq(1L),
                eq(AdminActionType.REPORT_TARGET_DELETE),
                eq("REVIEW"),
                eq("40"),
                anyString(),
                isNull()
        );
    }

    @Test
    @DisplayName("아직 지원하지 않는 신고 대상은 삭제하지 않는다")
    void unsupportedReportedTargetCannotBeDeleted() {
        User admin = user(1L, "관리자");
        Report report = report(100L, 10L, ReportTargetType.COMMENT, 50L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(reportRepository.findDetailById(100L)).thenReturn(Optional.of(report));
        when(reportRepository.findByTargetAndStatusForUpdate(
                ReportTargetType.COMMENT,
                50L,
                ReportStatus.PENDING
        )).thenReturn(List.of(report));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> adminReportService.deleteReportedTarget(1L, 100L, new AdminReportTargetDeleteRequest(null))
        );

        assertEquals(ReportErrorCode.UNSUPPORTED_REPORT_TARGET, exception.getErrorCode());
        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);
        verifyNoInteractions(adminPostService, adminReviewService, notificationService);
    }

    @Test
    @DisplayName("존재하지 않는 관리자는 신고 대상을 삭제할 수 없다")
    void deleteReportedTargetRejectsMissingAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> adminReportService.deleteReportedTarget(1L, 100L, new AdminReportTargetDeleteRequest(null))
        );

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(adminPostService, adminReviewService, notificationService);
    }

    @Test
    @DisplayName("존재하지 않는 신고의 대상은 삭제할 수 없다")
    void deleteReportedTargetRejectsMissingReport() {
        User admin = user(1L, "관리자");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(reportRepository.findDetailById(100L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> adminReportService.deleteReportedTarget(1L, 100L, new AdminReportTargetDeleteRequest(null))
        );

        assertEquals(ReportErrorCode.REPORT_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(adminPostService, adminReviewService, notificationService);
    }

    @Test
    @DisplayName("이미 처리된 신고 대상은 삭제할 수 없다")
    void deleteReportedTargetRejectsProcessedReport() {
        User admin = user(1L, "관리자");
        Report report = report(100L);
        report.process(admin, ReportStatus.RESOLVED, "기존 처리");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(reportRepository.findDetailById(100L)).thenReturn(Optional.of(report));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> adminReportService.deleteReportedTarget(1L, 100L, new AdminReportTargetDeleteRequest(null))
        );

        assertEquals(ReportErrorCode.REPORT_ALREADY_PROCESSED, exception.getErrorCode());
        verifyNoInteractions(adminPostService, adminReviewService, notificationService);
    }

    @Test
    @DisplayName("같은 신고자에게 삭제 알림을 중복 요청하지 않는다")
    void deleteReportedTargetRemovesDuplicateReporterIds() {
        User admin = user(1L, "관리자");
        Report firstReport = report(100L, 10L);
        Report secondReport = report(101L, 10L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(reportRepository.findDetailById(100L)).thenReturn(Optional.of(firstReport));
        when(reportRepository.findByTargetAndStatusForUpdate(
                ReportTargetType.POST,
                30L,
                ReportStatus.PENDING
        )).thenReturn(List.of(firstReport, secondReport));

        adminReportService.deleteReportedTarget(1L, 100L, new AdminReportTargetDeleteRequest(null));

        verify(notificationService).sendTargetDeletedAfterCommit(
                ReportTargetType.POST,
                List.of(10L),
                20L,
                100L
        );
    }

    private Report report(Long id) {
        return report(id, 10L);
    }

    private void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    private Report report(Long id, Long reporterId) {
        return report(id, reporterId, ReportTargetType.POST, 30L);
    }

    private Report report(
            Long id,
            Long reporterId,
            ReportTargetType targetType,
            Long targetId
    ) {
        User reporter = user(reporterId, "신고자");
        User reportedUser = user(20L, "피신고자");
        Report report = Report.create(
                targetType,
                targetId,
                reporter,
                reportedUser,
                ReportReason.SPAM,
                "신고 상세 내용",
                "신고 당시 원문"
        );
        ReflectionTestUtils.setField(report, "id", id);
        ReflectionTestUtils.setField(report, "createdAt", LocalDateTime.of(2026, 9, 13, 12, 0));
        return report;
    }

    private User user(Long id, String nickname) {
        User user = org.mockito.Mockito.mock(User.class);
        org.mockito.Mockito.lenient().when(user.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(user.getNickname()).thenReturn(nickname);
        return user;
    }
}
