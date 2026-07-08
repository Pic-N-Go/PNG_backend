package com.project.picngo.spot.controller;

import com.project.picngo.spot.dto.BookmarkResponse;
import com.project.picngo.spot.dto.NearbySpotResponse;
import com.project.picngo.spot.dto.PhotogenicResponse;
import com.project.picngo.spot.dto.RecommendedSpotResponse;
import com.project.picngo.spot.dto.ReviewListResponse;
import com.project.picngo.spot.dto.ReviewRequest;
import com.project.picngo.spot.dto.ReviewResponse;
import com.project.picngo.spot.dto.SpotDetailResponse;
import com.project.picngo.spot.dto.SpotPhotoResponse;
import com.project.picngo.spot.service.BookmarkService;
import com.project.picngo.spot.service.PhotogenicService;
import com.project.picngo.spot.service.ReviewService;
import com.project.picngo.spot.service.SpotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/spots")
public class SpotController implements SpotControllerApiSpec {

    private final SpotService spotService;
    private final ReviewService reviewService;
    private final BookmarkService bookmarkService;
    private final PhotogenicService photogenicService;

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
    public ResponseEntity<PhotogenicResponse> getPhotogenicScore(@PathVariable Long id) {
        return ResponseEntity.ok(photogenicService.calculate(id));
    }

    @PostMapping("/{id}/bookmark")
    public ResponseEntity<BookmarkResponse> toggleBookmark(@PathVariable Long id) {
        return ResponseEntity.ok(bookmarkService.toggle(id));
    }

    @GetMapping("/{id}/photos")
    public ResponseEntity<SpotPhotoResponse> getSpotPhotos(@PathVariable Long id) {
        return ResponseEntity.ok(spotService.getSpotPhotos(id));
    }

    @PostMapping("/{id}/reviews")
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody ReviewRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createReview(id, request));
    }
}
