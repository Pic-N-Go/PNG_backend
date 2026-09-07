package com.project.picngo.course.agent.dto;

import java.time.LocalDate;

public record AiCoursePlanRequest(
        String prompt,
        String region,
        LocalDate targetDate
) {}
