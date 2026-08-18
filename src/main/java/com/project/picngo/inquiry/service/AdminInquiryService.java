package com.project.picngo.inquiry.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.InquiryErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.inquiry.domain.Inquiry;
import com.project.picngo.inquiry.domain.InquiryStatus;
import com.project.picngo.inquiry.dto.InquiryResponse;
import com.project.picngo.inquiry.repository.InquiryRepository;
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

    /**
     * 관리자용 전체 1:1 문의 목록 페이징 및 필터/검색 조회
     */
    public Page<InquiryResponse> getInquiriesForAdmin(InquiryStatus status, Boolean isResolved, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        Page<Inquiry> inquiries = inquiryRepository.searchInquiriesForAdmin(status, isResolved, cleanKeyword, pageable);
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

        return InquiryResponse.from(inquiry);
    }
}
