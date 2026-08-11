package com.project.picngo.contest.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.contest.dto.ContestCreateEntryRequest;
import com.project.picngo.contest.dto.ContestEntryDetailResponse;
import com.project.picngo.contest.dto.ContestEntryPageResponse;
import com.project.picngo.contest.dto.ContestEntryResponse;
import com.project.picngo.contest.dto.ContestMyEntryResponse;
import com.project.picngo.contest.dto.ContestMyHistoryResponse;
import com.project.picngo.contest.dto.ContestMyVoteResponse;
import com.project.picngo.contest.dto.ContestPastPageResponse;
import com.project.picngo.contest.dto.ContestRankingHistoryResponse;
import com.project.picngo.contest.dto.ContestReportRequest;
import com.project.picngo.contest.dto.ContestResponse;
import com.project.picngo.contest.dto.ContestResultResponse;
import com.project.picngo.contest.dto.ContestSubscriptionResponse;
import com.project.picngo.contest.dto.ContestVoteResponse;
import com.project.picngo.contest.service.ContestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ContestController implements ContestControllerApiSpec {

    private final ContestService contestService;

    // 현재 콘테스트 조회
    @GetMapping("/contests/current")
    public ResponseEntity<ContestResponse> getCurrentContest(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(contestService.getCurrentContest(userDetails.getId()));
    }

    // 콘테스트 상세 조회
    @GetMapping("/contests/{contestId}")
    public ResponseEntity<ContestResponse> getContest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long contestId
    ) {
        return ResponseEntity.ok(contestService.getContest(contestId, userDetails.getId()));
    }

    // 지난 콘테스트 목록 조회
    @GetMapping("/contests")
    public ResponseEntity<ContestPastPageResponse> getPastContests(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                contestService.getPastContests(userDetails.getId(), PageRequest.of(page, size))
        );
    }

    // 출품작 목록 조회
    @GetMapping("/contests/{contestId}/entries")
    public ResponseEntity<ContestEntryPageResponse> getContestEntries(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long contestId,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                contestService.getContestEntries(contestId, userDetails.getId(), sort, page, size)
        );
    }

    // 출품작 등록
    @PostMapping(value = "/contests/{contestId}/entries", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContestEntryResponse> createEntry(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long contestId,
            @Valid @RequestPart("request") ContestCreateEntryRequest request,
            @RequestPart("photo") MultipartFile photo
    ) {
        return ResponseEntity.ok(
                contestService.createEntry(contestId, userDetails.getId(), request, photo)
        );
    }

    // 출품작 상세 조회
    @GetMapping("/contests/{contestId}/entries/{entryId}")
    public ResponseEntity<ContestEntryDetailResponse> getContestEntry(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long contestId,
            @PathVariable Long entryId
    ) {
        return ResponseEntity.ok(
                contestService.getContestEntry(contestId, entryId, userDetails.getId())
        );
    }

    // 출품작 삭제
    @DeleteMapping("/contests/{contestId}/entries/{entryId}")
    public ResponseEntity<Void> deleteEntry(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long contestId,
            @PathVariable Long entryId
    ) {
        contestService.deleteEntry(contestId, entryId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    // 투표하기
    @PostMapping("/contests/{contestId}/entries/{entryId}/vote")
    public ResponseEntity<ContestVoteResponse> voteEntry(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long contestId,
            @PathVariable Long entryId
    ) {
        return ResponseEntity.ok(
                contestService.voteEntry(contestId, entryId, userDetails.getId())
        );
    }

    // 투표 취소
    @DeleteMapping("/contests/{contestId}/entries/{entryId}/vote")
    public ResponseEntity<ContestVoteResponse> cancelVote(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long contestId,
            @PathVariable Long entryId
    ) {
        return ResponseEntity.ok(
                contestService.cancelVote(contestId, entryId, userDetails.getId())
        );
    }

    // 내 출품 현황 조회
    @GetMapping("/contests/{contestId}/my-entry")
    public ResponseEntity<ContestMyEntryResponse> getMyEntry(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long contestId
    ) {
        return ResponseEntity.ok(
                contestService.getMyEntry(contestId, userDetails.getId())
        );
    }

    // 내가 투표한 작품 조회
    @GetMapping("/contests/{contestId}/my-votes")
    public ResponseEntity<ContestMyVoteResponse> getMyVotes(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long contestId
    ) {
        return ResponseEntity.ok(
                contestService.getMyVotes(contestId, userDetails.getId())
        );
    }

    // 내 콘테스트 참여 기록 조회
    @GetMapping("/contests/my-history")
    public ResponseEntity<ContestMyHistoryResponse> getMyHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                contestService.getMyHistory(userDetails.getId())
        );
    }

    // 순위 집계 조회
    @GetMapping("/contests/{contestId}/ranking-history")
    public ResponseEntity<ContestRankingHistoryResponse> getRankingHistory(
            @PathVariable Long contestId
    ) {
        return ResponseEntity.ok(
                contestService.getRankingHistory(contestId)
        );
    }

    // 콘테스트 결과 조회
    @GetMapping("/contests/{contestId}/result")
    public ResponseEntity<ContestResultResponse> getContestResult(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long contestId
    ) {
        return ResponseEntity.ok(
                contestService.getContestResult(contestId, userDetails.getId())
        );
    }

    // 다음 콘테스트 알림 구독
    @PostMapping("/contests/{contestId}/subscribe")
    public ResponseEntity<ContestSubscriptionResponse> subscribeContest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long contestId
    ) {
        return ResponseEntity.ok(
                contestService.subscribeContest(contestId, userDetails.getId())
        );
    }

    // 다음 콘테스트 알림 구독 취소
    @DeleteMapping("/contests/{contestId}/subscribe")
    public ResponseEntity<ContestSubscriptionResponse> unsubscribeContest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long contestId
    ) {
        return ResponseEntity.ok(
                contestService.unsubscribeContest(contestId, userDetails.getId())
        );
    }

    // 출품작 신고
    @PostMapping("/contest-entries/{entryId}/report")
    public ResponseEntity<Void> reportEntry(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long entryId,
            @Valid @RequestBody ContestReportRequest request
    ) {
        contestService.reportEntry(entryId, userDetails.getId(), request);
        return ResponseEntity.noContent().build();
    }
}
