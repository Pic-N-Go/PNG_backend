package com.project.picngo.report.service;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ReportErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.community.service.AdminPostService;
import com.project.picngo.report.domain.Report;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final AdminPostService adminPostService;
    private final AdminReviewService adminReviewService;
    private final AdminReportNotificationService notificationService;

    public AdminReportDetailResponse getReport(Long reportId) {
        Report report = reportRepository.findDetailById(reportId)
                .orElseThrow(() -> new CustomException(ReportErrorCode.REPORT_NOT_FOUND));

        return AdminReportDetailResponse.from(report);
    }

    public Page<AdminReportListResponse> getAllReports(int page, int size) {
        return reportRepository.findAllForAdmin(pageable(page, size))
                .map(AdminReportListResponse::from);
    }

    public Page<AdminReportListResponse> getPendingReports(int page, int size) {
        return getReportsByStatus(ReportStatus.PENDING, page, size);
    }

    public Page<AdminReportListResponse> getResolvedReports(int page, int size) {
        return getReportsByStatus(ReportStatus.RESOLVED, page, size);
    }

    public Page<AdminReportListResponse> getDismissedReports(int page, int size) {
        return getReportsByStatus(ReportStatus.DISMISSED, page, size);
    }

    @Transactional
    public AdminReportProcessResponse processReport(Long adminUserId, Long reportId, AdminReportProcessRequest request) {
        if (request.status() == null || request.status() == ReportStatus.PENDING) {
            throw new CustomException(ReportErrorCode.INVALID_REPORT_STATUS);
        }

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new CustomException(ReportErrorCode.REPORT_NOT_FOUND));

        if (report.getStatus() != ReportStatus.PENDING) {
            throw new CustomException(ReportErrorCode.REPORT_ALREADY_PROCESSED);
        }

        report.process(admin, request.status(), request.resolutionNote());

        recordAuditLogAfterCommit(
                adminUserId,
                AdminActionType.REPORT_PROCESS,
                "REPORT",
                String.valueOf(reportId),
                "신고 처리 상태: " + request.status()
        );

        return AdminReportProcessResponse.from(report);
    }

    @Transactional
    public AdminReportProcessResponse deleteReportedTarget(
            Long adminUserId,
            Long reportId,
            AdminReportTargetDeleteRequest request
    ) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        Report report = reportRepository.findDetailById(reportId)
                .orElseThrow(() -> new CustomException(ReportErrorCode.REPORT_NOT_FOUND));

        if (report.getStatus() != ReportStatus.PENDING) {
            throw new CustomException(ReportErrorCode.REPORT_ALREADY_PROCESSED);
        }
        List<Report> relatedReports = reportRepository.findByTargetAndStatusForUpdate(
                report.getTargetType(),
                report.getTargetId(),
                ReportStatus.PENDING
        );
        Report targetReport = relatedReports.stream()
                .filter(relatedReport -> relatedReport.getId().equals(reportId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ReportErrorCode.REPORT_ALREADY_PROCESSED));
        List<Long> reporterIds = relatedReports.stream()
                .map(relatedReport -> relatedReport.getReporter().getId())
                .distinct()
                .toList();
        Long reportedUserId = report.getReportedUser().getId();

        deleteTarget(report.getTargetType(), report.getTargetId());

        String resolutionNote = request.resolutionNote() == null || request.resolutionNote().isBlank()
                ? createDeletionResolutionNote(report.getTargetType())
                : request.resolutionNote().trim();

        relatedReports.forEach(relatedReport -> relatedReport.process(
                admin,
                ReportStatus.RESOLVED,
                resolutionNote
        ));

        notificationService.sendTargetDeletedAfterCommit(
                report.getTargetType(),
                reporterIds,
                reportedUserId,
                reportId
        );

        recordAuditLogAfterCommit(
                adminUserId,
                AdminActionType.REPORT_TARGET_DELETE,
                report.getTargetType().name(),
                String.valueOf(report.getTargetId()),
                "신고 대상 삭제 및 관련 신고 처리: " + relatedReports.size() + "건"
        );

        return AdminReportProcessResponse.from(targetReport);
    }

    private void deleteTarget(ReportTargetType targetType, Long targetId) {
        switch (targetType) {
            case POST -> adminPostService.deletePostByAdmin(targetId);
            case REVIEW -> adminReviewService.deleteReviewByAdmin(targetId);
            default -> throw new CustomException(ReportErrorCode.UNSUPPORTED_REPORT_TARGET);
        }
    }

    private String createDeletionResolutionNote(ReportTargetType targetType) {
        return switch (targetType) {
            case POST -> "신고된 게시글 삭제";
            case REVIEW -> "신고된 리뷰 삭제";
            default -> "신고 대상 콘텐츠 삭제";
        };
    }

    /**
     * 신고 처리 트랜잭션이 성공적으로 커밋된 경우에만 완료 감사 로그를 기록한다.
     * AdminAuditLogService.record()는 별도 트랜잭션(REQUIRES_NEW)을 사용하므로 즉시 호출하면,
     * 신고 처리가 롤백되어도 감사 로그만 남을 수 있어 afterCommit 콜백으로 실행 시점을 늦춘다.
     */
    private void recordAuditLogAfterCommit(
            Long adminUserId,
            AdminActionType actionType,
            String targetType,
            String targetId,
            String detail
    ) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            adminAuditLogService.record(
                                    adminUserId,
                                    actionType,
                                    targetType,
                                    targetId,
                                    detail,
                                    null
                            );
                        } catch (Exception exception) {
                            // 비즈니스 처리는 이미 커밋됐으므로 감사 로그 실패를 별도로 기록하고 전파하지 않는다.
                            log.warn(
                                    "관리자 감사 로그 기록 실패: actionType={}, targetId={}, message={}",
                                    actionType,
                                    targetId,
                                    exception.getMessage()
                            );
                        }
                    }
                }
        );
    }

    private Page<AdminReportListResponse> getReportsByStatus(ReportStatus status, int page, int size) {
        return reportRepository.findByStatus(status, pageable(page, size))
                .map(AdminReportListResponse::from);
    }

    private Pageable pageable(int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

        return PageRequest.of(normalizedPage, normalizedSize, sort);
    }

}
