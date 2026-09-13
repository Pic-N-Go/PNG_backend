package com.project.picngo.course.mq;

import java.time.LocalDate;

public record AiCoursePlanMessage(
        String taskId,
        Long userId,
        String prompt,
        String region,
        LocalDate targetDate
) {}
