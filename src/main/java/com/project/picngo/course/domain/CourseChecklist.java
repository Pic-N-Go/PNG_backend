package com.project.picngo.course.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 200)
    private String content;

    @Column(nullable = false)
    private Boolean isChecked = false;

    @Builder
    public CourseChecklist(Course course, String content) {
        this.course = course;
        this.content = content;
        this.isChecked = false;
    }

    public void toggleChecked() {
        this.isChecked = !this.isChecked;
    }

    public void updateContent(String content) {
        this.content = content;
    }
}



