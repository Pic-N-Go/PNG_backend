package com.project.picngo.inquiry.service;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.InquiryErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.inquiry.domain.Inquiry;
import com.project.picngo.inquiry.domain.InquiryStatus;
import com.project.picngo.inquiry.domain.InquiryType;
import com.project.picngo.inquiry.dto.InquiryResponse;
import com.project.picngo.inquiry.repository.InquiryRepository;
import com.project.picngo.notification.service.NotificationService;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminInquiryService {

    private final InquiryRepository inquiryRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AdminAuditLogService adminAuditLogService;

    /**
     * 관리자용 전체 1:1 문의 목록 페이징 및 필터/검색 조회
     */
    public Page<InquiryResponse> getInquiriesForAdmin(InquiryType type, InquiryStatus status, Boolean isResolved, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        Page<Inquiry> inquiries = inquiryRepository.searchInquiriesForAdmin(type, status, isResolved, cleanKeyword, pageable);
        return inquiries.map(InquiryResponse::from);
    }

    /**
     * 관리자 1:1 문의 답변 작성 및 수정
     */
    @Transactional
    public InquiryResponse answerInquiry(Long adminUserId, Long inquiryId, String answer) {
        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new CustomException(InquiryErrorCode.INQUIRY_NOT_FOUND));

        inquiry.updateAnswer(adminUser, answer.trim());
        log.info("관리자 1:1 문의 답변 완료: inquiryId={}, adminUserId={}", inquiryId, adminUserId);

        // 1. 사용자에게 1:1 문의 답변 등록 비동기 푸시 및 인앱 알림 발송 (RabbitMQ)
        try {
            notificationService.sendPushNotification(
                    inquiry.getUser().getId(),
                    "INQUIRY_ANSWER",
                    "1:1 문의에 답변이 등록되었습니다.",
                    "작성하신 문의 [" + inquiry.getTitle() + "]에 관리자 답변이 등록되었습니다.",
                    "/mypage/inquiry/" + inquiry.getId()
            );
        } catch (Exception e) {
            log.warn("1:1 문의 답변 푸시 알림 비동기 큐 전송 실패 (userId={}): {}", inquiry.getUser().getId(), e.getMessage());
        }

        // 2. 관리자 감사 로그 기록
        try {
            adminAuditLogService.record(
                    adminUserId,
                    AdminActionType.INQUIRY_ANSWER,
                    "INQUIRY",
                    String.valueOf(inquiryId),
                    String.format("1:1 문의 [#%d - %s] 관리자 답변 등록", inquiryId, inquiry.getTitle()),
                    null
            );
        } catch (Exception e) {
            log.warn("1:1 문의 답변 감사 로그 기록 실패: {}", e.getMessage());
        }

        return InquiryResponse.from(inquiry);
    }
}
