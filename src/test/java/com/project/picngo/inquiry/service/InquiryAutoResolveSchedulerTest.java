package com.project.picngo.inquiry.service;

import com.project.picngo.inquiry.domain.InquiryStatus;
import com.project.picngo.inquiry.repository.InquiryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InquiryAutoResolveSchedulerTest {

    @Mock
    private InquiryRepository inquiryRepository;

    @InjectMocks
    private InquiryAutoResolveScheduler scheduler;

    @Test
    @DisplayName("7일 미응답 문의 벌크 업데이트 성공 테스트")
    void autoResolveOldInquiries_Success() {
        // given
        given(inquiryRepository.bulkAutoResolveInquiries(
                eq(InquiryStatus.ANSWERED),
                eq(InquiryStatus.RESOLVED),
                any(LocalDateTime.class)
        )).willReturn(5);

        // when
        int updatedCount = scheduler.autoResolveOldInquiries();

        // then
        assertThat(updatedCount).isEqualTo(5);
        verify(inquiryRepository).bulkAutoResolveInquiries(
                eq(InquiryStatus.ANSWERED),
                eq(InquiryStatus.RESOLVED),
                any(LocalDateTime.class)
        );
    }
}
