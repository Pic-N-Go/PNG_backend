package com.project.picngo.spot.controller;

import com.project.picngo.spot.dto.SpotResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Spot", description = "Spot list and search API")
public interface SpotControllerApiSpec {

    @Operation(summary = "Get spot list", description = "Returns active spots with optional category filtering.")
    ResponseEntity<Page<SpotResponse>> getSpots(
            @Parameter(description = "Spot category") @RequestParam(required = false) String category,
            @Parameter(description = "Sort option: latest, popular, score") @RequestParam(defaultValue = "latest") String sort,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "Search spots", description = "Searches active spots by keyword and optional category.")
    ResponseEntity<Page<SpotResponse>> searchSpots(
            @Parameter(description = "Search keyword") @RequestParam String keyword,
            @Parameter(description = "Spot category") @RequestParam(required = false) String category,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size
    );
}
