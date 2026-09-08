package com.project.picngo.contest.controller;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.contest.domain.ContestPhase;
import com.project.picngo.contest.domain.ContestReportReason;
import com.project.picngo.contest.dto.AdminContestDetailResponse;
import com.project.picngo.contest.dto.AdminContestEntryResponse;
import com.project.picngo.contest.dto.AdminContestReportResponse;
import com.project.picngo.contest.dto.AdminContestSummaryResponse;
import com.project.picngo.contest.dto.ContestUpdateRequest;
import com.project.picngo.contest.service.ContestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContestAdminControllerTest {

    @Mock
    private ContestService contestService;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @Mock
    private CustomUserDetails adminUserDetails;

    @InjectMocks
    private ContestAdminController controller;

    @Test
    @DisplayName("관리자 콘테스트 목록을 정상 조회한다")
    void getAdminContests() {
        AdminContestSummaryResponse summary = new AdminContestSummaryResponse(
                1L, "가을 단풍전", "설명", "url", ContestPhase.SUBMITTING,
                LocalDateTime.now(), LocalDateTime.now().plusWeeks(2),
                LocalDateTime.now().plusWeeks(2), LocalDateTime.now().plusWeeks(4),
                LocalDateTime.now().plusWeeks(4).plusDays(1),
                3, 3, true, false, false, 10L, 50L, LocalDateTime.now()
        );
        Page<AdminContestSummaryResponse> page = new PageImpl<>(List.of(summary));
        given(contestService.getAdminContests(any(PageRequest.class))).willReturn(page);

        ResponseEntity<Page<AdminContestSummaryResponse>> response = controller.getAdminContests(0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        assertThat(response.getBody().getContent().get(0).title()).isEqualTo("가을 단풍전");
    }

    @Test
    @DisplayName("관리자 콘테스트 상세 정보를 정상 조회한다")
    void getAdminContestDetail() {
        AdminContestDetailResponse detail = new AdminContestDetailResponse(
                1L, "가을 단풍전", "설명", "url", ContestPhase.SUBMITTING,
                LocalDateTime.now(), LocalDateTime.now().plusWeeks(2),
                LocalDateTime.now().plusWeeks(2), LocalDateTime.now().plusWeeks(4),
                LocalDateTime.now().plusWeeks(4).plusDays(1),
                3, 3, true, false, false, 5L, 10L, 8L, 50L,
                LocalDateTime.now(), LocalDateTime.now()
        );
        given(contestService.getAdminContestDetail(1L)).willReturn(detail);

        ResponseEntity<AdminContestDetailResponse> response = controller.getAdminContestDetail(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().contestId()).isEqualTo(1L);
        assertThat(response.getBody().subscriberCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("콘테스트 정보 및 일정을 수정하고 감사 로그를 기록한다")
    void updateContest() {
        given(adminUserDetails.getId()).willReturn(99L);
        ContestUpdateRequest request = new ContestUpdateRequest(
                "수정된 단풍전", "수정된 설명", "new-url.jpg", LocalDateTime.now().plusWeeks(1)
        );
        AdminContestDetailResponse updatedDetail = new AdminContestDetailResponse(
                1L, "수정된 단풍전", "수정된 설명", "new-url.jpg", ContestPhase.UPCOMING,
                LocalDateTime.now().plusWeeks(1), LocalDateTime.now().plusWeeks(3),
                LocalDateTime.now().plusWeeks(3), LocalDateTime.now().plusWeeks(5),
                LocalDateTime.now().plusWeeks(5).plusDays(1),
                3, 3, true, false, false, 5L, 0L, 0L, 0L,
                LocalDateTime.now(), LocalDateTime.now()
        );
        given(contestService.updateContest(eq(1L), any(ContestUpdateRequest.class))).willReturn(updatedDetail);

        ResponseEntity<AdminContestDetailResponse> response = controller.updateContest(adminUserDetails, 1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().title()).isEqualTo("수정된 단풍전");

        verify(adminAuditLogService, times(1)).record(
                eq(99L),
                eq(AdminActionType.CONTEST_UPDATE),
                eq("CONTEST"),
                eq("1"),
                contains("수정된 단풍전"),
                isNull()
        );
    }

    @Test
    @DisplayName("콘테스트 시작 알림을 수동 발송하고 감사 로그를 기록한다")
    void sendStartNotification() {
        given(adminUserDetails.getId()).willReturn(99L);
        given(contestService.sendStartNotification(1L)).willReturn(12);

        ResponseEntity<Map<String, Object>> response = controller.sendStartNotification(adminUserDetails, 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("sentCount")).isEqualTo(12);

        verify(adminAuditLogService, times(1)).record(
                eq(99L),
                eq(AdminActionType.CONTEST_NOTIFICATION_SEND),
                eq("CONTEST"),
                eq("1"),
                contains("수신자: 12명"),
                isNull()
        );
    }

    @Test
    @DisplayName("콘테스트 결과를 강제 발표하고 감사 로그를 기록한다")
    void publishResult() {
        given(adminUserDetails.getId()).willReturn(99L);
        AdminContestDetailResponse detail = new AdminContestDetailResponse(
                1L, "가을 단풍전", "설명", "url", ContestPhase.ENDED,
                LocalDateTime.now().minusWeeks(4), LocalDateTime.now().minusWeeks(2),
                LocalDateTime.now().minusWeeks(2), LocalDateTime.now(),
                LocalDateTime.now(),
                3, 3, true, true, true, 5L, 10L, 8L, 50L,
                LocalDateTime.now(), LocalDateTime.now()
        );
        given(contestService.forcePublishResult(1L)).willReturn(detail);

        ResponseEntity<AdminContestDetailResponse> response = controller.publishResult(adminUserDetails, 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().phase()).isEqualTo(ContestPhase.ENDED);

        verify(adminAuditLogService, times(1)).record(
                eq(99L),
                eq(AdminActionType.CONTEST_RESULT_PUBLISH),
                eq("CONTEST"),
                eq("1"),
                contains("가을 단풍전"),
                isNull()
        );
    }

    @Test
    @DisplayName("콘테스트 결과 발표 알림을 수동 발송하고 감사 로그를 기록한다")
    void sendResultNotification() {
        given(adminUserDetails.getId()).willReturn(99L);
        given(contestService.sendResultNotification(1L)).willReturn(25);

        ResponseEntity<Map<String, Object>> response = controller.sendResultNotification(adminUserDetails, 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("sentCount")).isEqualTo(25);

        verify(adminAuditLogService, times(1)).record(
                eq(99L),
                eq(AdminActionType.CONTEST_RESULT_NOTIFICATION_SEND),
                eq("CONTEST"),
                eq("1"),
                contains("수신자: 25명"),
                isNull()
        );
    }

    @Test
    @DisplayName("특정 콘테스트의 출품작 목록을 정상 조회한다")
    void getAdminContestEntries() {
        AdminContestEntryResponse entry = new AdminContestEntryResponse(
                10L, 1L, 2L, "사진작가", "profile.jpg", 100L, "남산서울타워",
                "photo.jpg", "단풍 든 남산", 15, 2L, LocalDateTime.now()
        );
        Page<AdminContestEntryResponse> page = new PageImpl<>(List.of(entry));
        given(contestService.getAdminContestEntries(eq(1L), any(PageRequest.class))).willReturn(page);

        ResponseEntity<Page<AdminContestEntryResponse>> response = controller.getAdminContestEntries(1L, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        assertThat(response.getBody().getContent().get(0).reportCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("출품작을 관리자 권한으로 강제 삭제하고 감사 로그를 남긴다")
    void adminDeleteEntry() {
        given(adminUserDetails.getId()).willReturn(99L);
        doNothing().when(contestService).adminDeleteEntry(10L, 99L, "부적절한 사진");

        ResponseEntity<Void> response = controller.adminDeleteEntry(adminUserDetails, 10L, "부적절한 사진");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(contestService, times(1)).adminDeleteEntry(10L, 99L, "부적절한 사진");
        verify(adminAuditLogService, times(1)).record(
                eq(99L),
                eq(AdminActionType.CONTEST_ENTRY_DELETE),
                eq("CONTEST_ENTRY"),
                eq("10"),
                contains("사유: 부적절한 사진"),
                isNull()
        );
    }

    @Test
    @DisplayName("신고 접수 목록을 정상 조회한다")
    void getAdminReports() {
        AdminContestReportResponse report = new AdminContestReportResponse(
                100L, 10L, "photo.jpg", "캡션", 2L, "피신고자",
                3L, "신고자", ContestReportReason.SPAM, "스팸 도배입니다", LocalDateTime.now()
        );
        Page<AdminContestReportResponse> page = new PageImpl<>(List.of(report));
        given(contestService.getAdminReports(any(PageRequest.class))).willReturn(page);

        ResponseEntity<Page<AdminContestReportResponse>> response = controller.getAdminReports(0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent().get(0).reason()).isEqualTo(ContestReportReason.SPAM);
    }
}
