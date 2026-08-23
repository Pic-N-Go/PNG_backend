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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "콘테스트 (Contest)", description = "커뮤니티 콘테스트 API")
public interface ContestControllerApiSpec {

    @Operation(
            summary = "현재 콘테스트 조회",
            description = "현재 진행 중인 콘테스트 정보를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestResponse> getCurrentContest(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(
            summary = "다음 예정 콘테스트 조회",
            description = "아직 출품이 시작되지 않은 가장 이른 콘테스트를 조회합니다. 예정된 회차가 없으면 204를 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestResponse> getUpcomingContest(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(
            summary = "콘테스트 상세 조회",
            description = "특정 콘테스트의 기본 정보와 내 참여 상태를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestResponse> getContest(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "콘테스트 ID") @PathVariable Long contestId
    );

    @Operation(
            summary = "지난 콘테스트 목록 조회",
            description = "결과 발표가 끝난 지난 콘테스트 목록을 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestPastPageResponse> getPastContests(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "페이지 번호", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "출품작 목록 조회",
            description = "콘테스트에 등록된 출품작 목록을 조회합니다. sort는 latest 또는 votes를 사용할 수 있습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestEntryPageResponse> getContestEntries(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "콘테스트 ID") @PathVariable Long contestId,
            @Parameter(description = "정렬 기준", example = "latest") @RequestParam(defaultValue = "latest") String sort,
            @Parameter(description = "페이지 번호", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "출품작 등록",
            description = "출품 기간에 사진을 업로드하여 콘테스트에 출품합니다. 사용자당 최대 3장까지 가능합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestEntryResponse> createEntry(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "콘테스트 ID") @PathVariable Long contestId,
            @Valid @RequestPart("request") ContestCreateEntryRequest request,
            @Parameter(
                    description = "출품 사진",
                    content = @Content(mediaType = "image/*", schema = @Schema(type = "string", format = "binary"))
            )
            @RequestPart("photo") MultipartFile photo
    );

    @Operation(
            summary = "출품작 상세 조회",
            description = "특정 출품작의 상세 정보, 투표 여부, 남은 투표 수를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestEntryDetailResponse> getContestEntry(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "콘테스트 ID") @PathVariable Long contestId,
            @Parameter(description = "출품작 ID") @PathVariable Long entryId
    );

    @Operation(
            summary = "출품작 삭제",
            description = "내가 등록한 출품작을 삭제합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<Void> deleteEntry(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "콘테스트 ID") @PathVariable Long contestId,
            @Parameter(description = "출품작 ID") @PathVariable Long entryId
    );

    @Operation(
            summary = "출품작 투표",
            description = "투표 기간에 출품작에 투표합니다. 투표는 콘테스트 기간 전체 기준 최대 3표입니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestVoteResponse> voteEntry(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "콘테스트 ID") @PathVariable Long contestId,
            @Parameter(description = "출품작 ID") @PathVariable Long entryId
    );

    @Operation(
            summary = "투표 취소",
            description = "내가 투표한 출품작의 투표를 취소합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestVoteResponse> cancelVote(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "콘테스트 ID") @PathVariable Long contestId,
            @Parameter(description = "출품작 ID") @PathVariable Long entryId
    );

    @Operation(
            summary = "내 출품 현황 조회",
            description = "현재 콘테스트에서 내가 출품한 작품 목록을 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestMyEntryResponse> getMyEntry(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "콘테스트 ID") @PathVariable Long contestId
    );

    @Operation(
            summary = "내 투표 작품 조회",
            description = "현재 콘테스트에서 내가 투표한 작품 목록을 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestMyVoteResponse> getMyVotes(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "콘테스트 ID") @PathVariable Long contestId
    );

    @Operation(
            summary = "내 콘테스트 참여 기록 조회",
            description = "내가 참여한 콘테스트 기록과 받은 표 수, 최고 순위를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestMyHistoryResponse> getMyHistory(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(
            summary = "순위 집계 조회",
            description = "매일 자정 1회 집계된 콘테스트 순위 변동 정보를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestRankingHistoryResponse> getRankingHistory(
            @Parameter(description = "콘테스트 ID") @PathVariable Long contestId
    );

    @Operation(
            summary = "콘테스트 결과 조회",
            description = "결과 발표 이후 최종 순위와 수상작을 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestResultResponse> getContestResult(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "콘테스트 ID") @PathVariable Long contestId
    );

    @Operation(
            summary = "콘테스트 알림 구독",
            description = "다음 콘테스트 시작 알림을 구독합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestSubscriptionResponse> subscribeContest(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "콘테스트 ID") @PathVariable Long contestId
    );

    @Operation(
            summary = "콘테스트 알림 구독 취소",
            description = "다음 콘테스트 시작 알림 구독을 취소합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestSubscriptionResponse> unsubscribeContest(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "콘테스트 ID") @PathVariable Long contestId
    );

    @Operation(
            summary = "출품작 신고",
            description = "부적절한 콘테스트 출품작을 신고합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<Void> reportEntry(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "출품작 ID") @PathVariable Long entryId,
            @Valid @org.springframework.web.bind.annotation.RequestBody ContestReportRequest request
    );
}
