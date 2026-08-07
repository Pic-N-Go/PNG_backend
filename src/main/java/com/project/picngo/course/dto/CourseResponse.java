package com.project.picngo.course.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CourseResponse(
        Long id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt
) {}
