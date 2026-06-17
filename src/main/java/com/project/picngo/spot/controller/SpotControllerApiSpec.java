package com.project.picngo.spot.controller;

import com.project.picngo.spot.dto.SpotResponse;
import com.project.picngo.spot.dto.SpotMapResponse;
import com.project.picngo.spot.dto.SpotSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

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

    @Operation(summary = "Get popular spots", description = "Returns popular active spots sorted by bookmark and review counts.")
    ResponseEntity<List<SpotResponse>> getPopularSpots(
            @Parameter(description = "Spot category") @RequestParam(required = false) String category,
            @Parameter(description = "Result size") @RequestParam(defaultValue = "10") int size
    );

    @Operation(summary = "Get map spots", description = "Returns active spot pins within the current map bounds.")
    ResponseEntity<List<SpotMapResponse>> getMapSpots(
            @Parameter(description = "South-west latitude") @RequestParam Double southWestLat,
            @Parameter(description = "South-west longitude") @RequestParam Double southWestLng,
            @Parameter(description = "North-east latitude") @RequestParam Double northEastLat,
            @Parameter(description = "North-east longitude") @RequestParam Double northEastLng,
            @Parameter(description = "Spot category") @RequestParam(required = false) String category
    );

    @Operation(summary = "Get spot summary", description = "Returns a summary card for a selected spot pin.")
    ResponseEntity<SpotSummaryResponse> getSpotSummary(
            @Parameter(description = "Spot ID") @PathVariable Long id
    );
}
