package com.project.picngo.inquiry.service;

import com.project.picngo.inquiry.domain.Inquiry;
import com.project.picngo.inquiry.domain.InquiryStatus;
import com.project.picngo.inquiry.repository.InquiryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 1:1 문의 7일 미응답 건 자동 해결(RESOLVED) 처리 스케줄러.
 * 관리자가 답변(ANSWERED)을 등록한 지 7일이 넘도록 사용자가 해결 여부 버튼을 누르지 않은 문의는
 * 매일 새벽 4시 배치를 통해 자동으로 해결 완료(RESOLVED) 상태로 변경합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryAutoResolveScheduler {

    private final InquiryRepository inquiryRepository;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void scheduleAutoResolve() {
        int count = autoResolveOldInquiries();
        if (count > 0) {
            log.info("1:1 문의 자동 해결 배치 완료: 총 {}건 해결 처리됨", count);
        }
    }

    @Transactional
    public int autoResolveOldInquiries() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Inquiry> targetInquiries = inquiryRepository.findByStatusAndIsResolvedFalseAndAnsweredAtBefore(
                InquiryStatus.ANSWERED,
                sevenDaysAgo
        );

        if (targetInquiries.isEmpty()) {
            return 0;
        }

        for (Inquiry inquiry : targetInquiries) {
            inquiry.updateResolved(true);
        }

        return targetInquiries.size();
    }
}
