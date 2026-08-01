package com.project.picngo.spot.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.spot.dto.*;
import com.project.picngo.spot.service.ChecklistService;
import com.project.picngo.spot.service.PhotogenicService;
import com.project.picngo.spot.service.ReviewService;
import com.project.picngo.spot.service.SpotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/spots")
public class SpotController implements SpotControllerApiSpec {

    private final SpotService spotService;
    private final ReviewService reviewService;
    private final PhotogenicService photogenicService;
    private final ChecklistService checklistService;

    @GetMapping
    public ResponseEntity<Page<SpotResponse>> getSpots(
            @RequestParam(required = false) List<String> category,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(spotService.getSpots(category, sort, page, size));
    }

    @GetMapping("/popular")
    public ResponseEntity<List<SpotResponse>> getPopularSpots(
            @RequestParam(required = false) List<String> category,
            @RequestParam(defaultValue = "10") int size
    ){
        return ResponseEntity.ok(spotService.getPopularSpots(category, size));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<SpotResponse>> searchSpots(
            @RequestParam String keyword,
            @RequestParam(required = false) List<String> category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(spotService.searchSpots(keyword, category, page, size));
    }

    @GetMapping("/map")
    public ResponseEntity<List<SpotMapResponse>> getMapSpots(
            @ParameterObject @Valid MapBoundsRequest request
    ){
        return ResponseEntity.ok(spotService.getMapSpots(request));
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<SpotSummaryResponse> getSpotSummary(
            @PathVariable Long id
    ){
        return ResponseEntity.ok(spotService.getSpotSummary(id));
    }

    @GetMapping("/recommended")
    public ResponseEntity<List<RecommendedSpotResponse>> getRecommendedSpots(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(spotService.getRecommendedSpots(userDetails.getId(), limit));
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<NearbySpotResponse>> getNearbySpots(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "5.0") Double radiusKm,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(spotService.getNearbySpots(lat, lng, radiusKm, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SpotDetailResponse> getSpotDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(spotService.getSpotDetail(id, userDetails != null ? userDetails.getId() : null));
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity<ReviewListResponse> getReviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "LATEST") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(reviewService.getReviews(id, sort, page, size));
    }

    @GetMapping("/{id}/photogenic-score")
    public ResponseEntity<PhotogenicResponse> getPhotogenicScore(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime time) {
        return ResponseEntity.ok(photogenicService.calculate(id, date, time));
    }

    @GetMapping("/{id}/photos")
    public ResponseEntity<SpotPhotoResponse> getSpotPhotos(@PathVariable Long id) {
        return ResponseEntity.ok(spotService.getSpotPhotos(id));
    }

    @GetMapping("/{id}/checklist")
    public ResponseEntity<ChecklistResponse> getChecklist(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(checklistService.getChecklist(id, userDetails.getId()));
    }

    @PostMapping("/{id}/checklist")
    public ResponseEntity<ChecklistResponse.ChecklistItemDto> addChecklistItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ChecklistRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(checklistService.addItem(id, request, userDetails.getId()));
    }

    @DeleteMapping("/{id}/checklist/{itemId}")
    public ResponseEntity<Void> deleteChecklistItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @PathVariable Long itemId
    ) {
        checklistService.deleteItem(id, itemId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/checklist/default/{defaultItemId}")
    public ResponseEntity<Void> hideDefaultChecklistItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @PathVariable Integer defaultItemId
    ) {
        checklistService.hideDefaultItem(id, defaultItemId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/checklist/default/{defaultItemId}/restore")
    public ResponseEntity<Void> restoreDefaultChecklistItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @PathVariable Integer defaultItemId
    ) {
        checklistService.restoreDefaultItem(id, defaultItemId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value ="/{id}/reviews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReviewResponse> createReview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestPart("request") ReviewRequest request,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.createReview(userDetails.getId(), id, request, photos));
    }
}
