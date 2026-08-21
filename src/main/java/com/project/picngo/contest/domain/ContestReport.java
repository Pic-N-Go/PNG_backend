package com.project.picngo.contest.domain;

import com.project.picngo.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contest_report_user_entry",
                        columnNames = {"user_id", "entry_id"}
                )
        }
)
// 출품작 신고 내역 저장
public class ContestReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    private ContestEntry entry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContestReportReason reason;

    @Column(length = 500)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static ContestReport create(
            ContestEntry entry,
            User user,
            ContestReportReason reason,
            String content
    ) {
        ContestReport report = new ContestReport();
        report.entry = entry;
        report.user = user;
        report.reason = reason;
        report.content = content;
        report.createdAt = LocalDateTime.now();
        return report;
    }
}
