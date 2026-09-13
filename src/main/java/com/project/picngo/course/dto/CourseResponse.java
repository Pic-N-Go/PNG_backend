package com.project.picngo.course.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CourseResponse(
        Long id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt,
        List<String> thumbnailUrls,
        Integer spotCount
) {
    public CourseResponse(Long id, String title, LocalDate startDate, LocalDate endDate, LocalDateTime createdAt) {
        this(id, title, startDate, endDate, createdAt, List.of(), 0);
    }
}
