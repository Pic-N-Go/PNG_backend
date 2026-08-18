package com.project.picngo.inquiry.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "inquiries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answered_by_id")
    private User answeredBy;

    private LocalDateTime answeredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InquiryType type;

    @Column(nullable = false)
    private boolean isResolved = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status = InquiryStatus.PENDING;

    @Builder
    private Inquiry(User user, InquiryType type, String title, String content) {
        this.user = user;
        this.type = type != null ? type : InquiryType.OTHER;
        this.title = title;
        this.content = content;
        this.isResolved = false;
        this.status = InquiryStatus.PENDING;
    }

    public static Inquiry create(User user, InquiryType type, String title, String content) {
        return Inquiry.builder()
                .user(user)
                .type(type)
                .title(title)
                .content(content)
                .build();
    }

    /**
     * 관리자 답변 등록 및 수정
     */
    public void updateAnswer(User adminUser, String answer) {
        this.answeredBy = adminUser;
        this.answer = answer;
        this.answeredAt = LocalDateTime.now();
        if (!this.isResolved) {
            this.status = InquiryStatus.ANSWERED;
        }
    }

    /**
     * 사용자에 의한 해결 여부(isResolved) 변경
     */
    public void updateResolved(boolean isResolved) {
        this.isResolved = isResolved;
        if (isResolved) {
            this.status = InquiryStatus.RESOLVED;
        } else if (this.answer != null && !this.answer.trim().isEmpty()) {
            this.status = InquiryStatus.ANSWERED;
        } else {
            this.status = InquiryStatus.PENDING;
        }
    }
}
