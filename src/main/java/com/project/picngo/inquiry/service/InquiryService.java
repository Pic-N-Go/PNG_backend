package com.project.picngo.inquiry.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.InquiryErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.inquiry.domain.Inquiry;
import com.project.picngo.inquiry.dto.InquiryCreateRequest;
import com.project.picngo.inquiry.dto.InquiryResponse;
import com.project.picngo.inquiry.repository.InquiryRepository;
import com.project.picngo.user.domain.Role;
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
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final UserRepository userRepository;

    /**
     * 사용자 1:1 문의 신규 등록
     */
    @Transactional
    public InquiryResponse createInquiry(Long userId, InquiryCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        Inquiry inquiry = Inquiry.create(user, request.title().trim(), request.content().trim());
        Inquiry saved = inquiryRepository.save(inquiry);

        log.info("신규 1:1 문의 등록 완료: inquiryId={}, userId={}", saved.getId(), userId);
        return InquiryResponse.from(saved);
    }

    /**
     * 내가 작성한 1:1 문의 목록 페이징 조회
     */
    public Page<InquiryResponse> getMyInquiries(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Inquiry> inquiries = inquiryRepository.findByUserId(userId, pageable);
        return inquiries.map(InquiryResponse::from);
    }

    /**
     * 1:1 문의 단건 상세 조회 (본인 또는 ADMIN 접근 허용)
     */
    public InquiryResponse getInquiryDetail(Long userId, Long inquiryId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new CustomException(InquiryErrorCode.INQUIRY_NOT_FOUND));

        // 작성자 본인이거나 ADMIN 권한인 경우에만 접근 허용
        if (!inquiry.getUser().getId().equals(userId) && user.getRole() != Role.ADMIN) {
            throw new CustomException(InquiryErrorCode.UNAUTHORIZED_INQUIRY_ACCESS);
        }

        return InquiryResponse.from(inquiry);
    }

    /**
     * 사용자 1:1 문의 해결 여부(isResolved) 변경
     */
    @Transactional
    public InquiryResponse updateResolveStatus(Long userId, Long inquiryId, boolean isResolved) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new CustomException(InquiryErrorCode.INQUIRY_NOT_FOUND));

        // 작성자 본인만 해결 여부를 변경할 수 있음
        if (!inquiry.getUser().getId().equals(userId)) {
            throw new CustomException(InquiryErrorCode.UNAUTHORIZED_INQUIRY_ACCESS);
        }

        inquiry.updateResolved(isResolved);
        log.info("1:1 문의 해결 상태 변경 완료: inquiryId={}, userId={}, isResolved={}", inquiryId, userId, isResolved);

        return InquiryResponse.from(inquiry);
    }
}
