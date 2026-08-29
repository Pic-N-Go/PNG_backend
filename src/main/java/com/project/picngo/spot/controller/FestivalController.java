package com.project.picngo.spot.controller;

import com.project.picngo.spot.dto.FestivalResponse;
import com.project.picngo.spot.service.FestivalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/festivals")
public class FestivalController implements FestivalControllerApiSpec {

    private final FestivalService festivalService;

    @Override
    @GetMapping
    public ResponseEntity<Page<FestivalResponse>> getFestivals(
            @RequestParam(required = false, defaultValue = "ONGOING") String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<FestivalResponse> response = festivalService.getFestivals(status, date, page, size);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<FestivalResponse> getFestivalById(@PathVariable Long id) {
        FestivalResponse response = festivalService.getFestivalById(id);
        return ResponseEntity.ok(response);
    }
}
