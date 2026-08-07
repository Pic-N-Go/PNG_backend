package com.project.picngo.course.dto;

public record CourseChecklistResponse(
        Long id,
        String content,
        Boolean isChecked
) {}
