package com.project.picngo.inquiry.service;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import com.project.picngo.inquiry.domain.Inquiry;
import com.project.picngo.inquiry.domain.InquiryStatus;
import com.project.picngo.inquiry.domain.InquiryType;
import com.project.picngo.inquiry.dto.InquiryResponse;
import com.project.picngo.inquiry.repository.InquiryRepository;
import com.project.picngo.notification.service.NotificationService;
import com.project.picngo.user.domain.Role;
import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminInquiryServiceTest {

    @Mock
    private InquiryRepository inquiryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @InjectMocks
    private AdminInquiryService adminInquiryService;

    @Test
    @DisplayName("관리자 1:1 문의 답변 등록 성공 시 FCM 푸시 알림 비동기 큐 전송 및 감사 로그가 기록된다")
    void answerInquiry_success_and_triggers_push_and_audit() {
        // given
        User normalUser = User.builder()
                .email("user@example.com")
                .nickname("일반회원")
                .role(Role.USER)
                .provider(SocialProvider.KAKAO)
                .providerId("12345")
                .build();
        ReflectionTestUtils.setField(normalUser, "id", 10L);

        User adminUser = User.builder()
                .email("admin@picngo.com")
                .nickname("최고관리자")
                .role(Role.ADMIN)
                .provider(SocialProvider.LOCAL)
                .providerId("admin")
                .build();
        ReflectionTestUtils.setField(adminUser, "id", 1L);

        Inquiry inquiry = Inquiry.create(normalUser, InquiryType.FEATURE, "기능 질문", "문의 본문");
        ReflectionTestUtils.setField(inquiry, "id", 100L);

        given(userRepository.findById(1L)).willReturn(Optional.of(adminUser));
        given(inquiryRepository.findById(100L)).willReturn(Optional.of(inquiry));

        // when
        InquiryResponse response = adminInquiryService.answerInquiry(1L, 100L, "관리자 친절 답변입니다.");

        // then
        assertThat(response.answer()).isEqualTo("관리자 친절 답변입니다.");
        assertThat(response.status()).isEqualTo(InquiryStatus.ANSWERED);

        // 1) FCM 푸시 알림 발송 검증
        verify(notificationService).sendPushNotification(
                eq(10L),
                eq("INQUIRY_ANSWER"),
                anyString(),
                anyString(),
                eq("/mypage/inquiry/100")
        );

        // 2) 관리자 감사 로그 기록 검증
        verify(adminAuditLogService).record(
                eq(1L),
                eq(AdminActionType.INQUIRY_ANSWER),
                eq("INQUIRY"),
                eq("100"),
                anyString(),
                isNull()
        );
    }
}
