package com.project.picngo.course.repository;

import com.project.picngo.course.domain.CourseChecklist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseChecklistRepository extends JpaRepository<CourseChecklist, Long> {
}
