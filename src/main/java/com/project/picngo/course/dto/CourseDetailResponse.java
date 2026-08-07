package com.project.picngo.course.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CourseDetailResponse(
        Long id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt,
        List<CourseSpotResponse> spots,
        List<CourseChecklistResponse> checklists
) {}
