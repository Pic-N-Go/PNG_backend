package com.project.picngo.course.dto;

import java.time.LocalDate;

public record CourseCreateRequest(
        String title,
        LocalDate startDate,
        LocalDate endDate
) {}
