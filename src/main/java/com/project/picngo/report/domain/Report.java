package com.project.picngo.report.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "reports",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_report_reporter_target",
                        columnNames = {"reporter_id", "target_type", "target_id"}
                )
        },
        indexes = {
                @Index(name = "idx_report_status_created_at", columnList = "status, created_at"),
                @Index(name = "idx_report_target", columnList = "target_type, target_id"),
                @Index(name = "idx_report_reported_user_created_at", columnList = "reported_user_id, created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_user_id", nullable = false)
    private User reportedUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by_id")
    private User handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "target_content_snapshot", nullable = false, columnDefinition = "TEXT")
    private String targetContentSnapshot;

    private Report(
            ReportTargetType targetType,
            Long targetId,
            User reporter,
            User reportedUser,
            ReportReason reason,
            String detail,
            String targetContentSnapshot
    ) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.reporter = reporter;
        this.reportedUser = reportedUser;
        this.reason = reason;
        this.detail = normalizeNullable(detail);
        this.status = ReportStatus.PENDING;
        this.targetContentSnapshot = targetContentSnapshot;
    }

    public static Report create(
            ReportTargetType targetType,
            Long targetId,
            User reporter,
            User reportedUser,
            ReportReason reason,
            String detail,
            String targetContentSnapshot
    ) {
        return new Report(
                targetType,
                targetId,
                reporter,
                reportedUser,
                reason,
                detail,
                targetContentSnapshot
        );
    }

    public void process(User handler, ReportStatus status, String resolutionNote) {
        if (status == null || status == ReportStatus.PENDING) {
            throw new IllegalArgumentException("처리 상태는 RESOLVED 또는 DISMISSED여야 합니다.");
        }

        this.status = status;
        this.handledBy = handler;
        this.handledAt = LocalDateTime.now();
        this.resolutionNote = normalizeNullable(resolutionNote);
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
