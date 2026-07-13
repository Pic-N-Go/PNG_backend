package com.project.picngo.spot.controller;

import com.project.picngo.spot.dto.ChecklistRequest;
import com.project.picngo.spot.dto.ChecklistResponse;
import com.project.picngo.spot.dto.NearbySpotResponse;
import com.project.picngo.spot.dto.PhotogenicResponse;
import com.project.picngo.spot.dto.RecommendedSpotResponse;
import com.project.picngo.spot.dto.ReviewListResponse;
import com.project.picngo.spot.dto.ReviewRequest;
import com.project.picngo.spot.dto.ReviewResponse;
import com.project.picngo.spot.dto.SpotDetailResponse;
import com.project.picngo.spot.dto.SpotPhotoResponse;
import com.project.picngo.spot.service.ChecklistService;
import com.project.picngo.spot.service.PhotogenicService;
import com.project.picngo.spot.service.ReviewService;
import com.project.picngo.spot.service.SpotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/recommended")
    public ResponseEntity<List<RecommendedSpotResponse>> getRecommendedSpots(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(spotService.getRecommendedSpots(limit));
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
    public ResponseEntity<SpotDetailResponse> getSpotDetail(@PathVariable Long id) {
        return ResponseEntity.ok(spotService.getSpotDetail(id));
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
    public ResponseEntity<ChecklistResponse> getChecklist(@PathVariable Long id) {
        return ResponseEntity.ok(checklistService.getChecklist(id));
    }

    @PostMapping("/{id}/checklist")
    public ResponseEntity<ChecklistResponse.ChecklistItemDto> addChecklistItem(
            @PathVariable Long id,
            @Valid @RequestBody ChecklistRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(checklistService.addItem(id, request));
    }

    @DeleteMapping("/{id}/checklist/{itemId}")
    public ResponseEntity<Void> deleteChecklistItem(@PathVariable Long id, @PathVariable Long itemId) {
        checklistService.deleteItem(id, itemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/checklist/default/{defaultItemId}")
    public ResponseEntity<Void> hideDefaultChecklistItem(@PathVariable Long id, @PathVariable Integer defaultItemId) {
        checklistService.hideDefaultItem(id, defaultItemId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/checklist/default/{defaultItemId}/restore")
    public ResponseEntity<Void> restoreDefaultChecklistItem(@PathVariable Long id, @PathVariable Integer defaultItemId) {
        checklistService.restoreDefaultItem(id, defaultItemId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reviews")
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable Long id,
            @Valid @RequestBody ReviewRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createReview(id, request));
    }
}
