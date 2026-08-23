package com.project.picngo.contest.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.contest.dto.ContestCreateRequest;
import com.project.picngo.contest.dto.ContestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "콘테스트 관리 (Admin)", description = "콘테스트 회차 개설 API")
public interface ContestAdminControllerApiSpec {

    @Operation(
            summary = "콘테스트 회차 개설",
            description = """
                    테마를 받아 새 회차를 만듭니다. 기간은 규칙에서 파생되며 요청으로 지정할 수 없습니다.
                    출품 2주 → 투표 2주 → 투표 종료 다음 날 오전 9시 결과 발표.
                    submitStartAt을 비우면 직전 회차의 결과 발표 시각에 이어 붙습니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestResponse> createContest(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @Valid @RequestBody ContestCreateRequest request
    );
}
