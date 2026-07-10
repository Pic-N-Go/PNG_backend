package com.project.picngo.course.service;

import com.project.picngo.course.dto.*;
import com.project.picngo.course.domain.Course;
import com.project.picngo.course.domain.CourseChecklist;
import com.project.picngo.course.domain.CourseSpot;
import com.project.picngo.course.repository.CourseChecklistRepository;
import com.project.picngo.course.repository.CourseRepository;
import com.project.picngo.course.repository.CourseSpotRepository;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.CourseErrorCode;
import com.project.picngo.common.exception.code.AuthErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseSpotRepository courseSpotRepository;
    private final CourseChecklistRepository courseChecklistRepository;
    private final UserRepository userRepository;


    // ==================== 코스 CRUD ====================

    @Transactional
    public CourseResponse createCourse(Long userId, CourseCreateRequest request) {
        validateUserExists(userId);
        
        Course course = Course.builder()
                .userId(userId)
                .title(request.title())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();

        Course saved = courseRepository.save(course);
        return toCourseResponse(saved);
    }

    public List<CourseResponse> getCourses(Long userId) {
        return courseRepository.findAllByUserId(userId).stream()
                .map(this::toCourseResponse)
                .toList();
    }

    public CourseDetailResponse getCourseDetail(Long userId, Long courseId) {
        Course course = findCourseOrThrow(courseId);
        validateCourseOwner(course, userId);

        List<CourseSpotResponse> spots = course.getCourseSpots().stream()
                .map(this::toCourseSpotResponse)
                .toList();

        List<CourseChecklistResponse> checklists = course.getCourseChecklists().stream()
                .map(this::toChecklistResponse)
                .toList();

        return new CourseDetailResponse(
                course.getId(),
                course.getTitle(),
                course.getStartDate(),
                course.getEndDate(),
                course.getCreatedAt(),
                spots,
                checklists
        );
    }

    @Transactional
    public CourseResponse updateCourse(Long userId, Long courseId, CourseCreateRequest request) {
        Course course = findCourseOrThrow(courseId);
        validateCourseOwner(course, userId);
        
        course.update(request.title(), request.startDate(), request.endDate());
        return toCourseResponse(course);
    }

    @Transactional
    public void deleteCourse(Long userId, Long courseId) {
        Course course = findCourseOrThrow(courseId);
        validateCourseOwner(course, userId);
        courseRepository.delete(course);
    }

    // ==================== 코스 스팟 관리 (Facade 전용 Internal) ====================

    @Transactional
    public CourseSpotResponse addCourseSpotInternal(Long userId, Long courseId, CourseSpotAddRequest request) {
        Course course = findCourseOrThrow(courseId);
        validateCourseOwner(course, userId);

        // 중복 순서 방지(Shift) 로직: 같은 일차에서 추가되는 순서보다 크거나 같은 기존 스팟들의 순서를 +1씩 밀어줌
        course.getCourseSpots().stream()
                .filter(cs -> cs.getDayNumber().equals(request.dayNumber()))
                .filter(cs -> cs.getSequenceOrder() >= request.sequenceOrder())
                .forEach(cs -> cs.updateOrder(cs.getSequenceOrder() + 1));

        CourseSpot courseSpot = CourseSpot.builder()
                .course(course)
                .spotId(request.spotId())
                .dayNumber(request.dayNumber())
                .sequenceOrder(request.sequenceOrder())
                .memo(request.memo())
                .travelTimeMinutes(null) // Facade에서 계산 후 업데이트
                .build();

        CourseSpot saved = courseSpotRepository.save(courseSpot);
        
        if (!course.getCourseSpots().contains(saved)) {
            course.getCourseSpots().add(saved);
        }
        
        return toCourseSpotResponse(saved);
    }

    @Transactional
    public Integer removeCourseSpotInternal(Long userId, Long courseId, Long spotId) {
        Course course = findCourseOrThrow(courseId);
        validateCourseOwner(course, userId);
        
        return course.getCourseSpots().stream()
                .filter(cs -> cs.getId().equals(spotId))
                .findFirst()
                .map(spot -> {
                    Integer dayNumber = spot.getDayNumber();
                    course.getCourseSpots().remove(spot);
                    courseSpotRepository.delete(spot);
                    return dayNumber;
                })
                .orElse(null);
    }

    @Transactional
    public Set<Integer> updateSpotOrderInternal(Long userId, Long courseId, CourseSpotOrderUpdateRequest request) {
        Course course = findCourseOrThrow(courseId);
        validateCourseOwner(course, userId);

        Map<Long, CourseSpot> spotMap = course.getCourseSpots().stream()
                .collect(Collectors.toMap(CourseSpot::getId, cs -> cs));

        Set<Integer> affectedDays = new HashSet<>();
        List<Long> orderedIds = request.spotIds();
        for (int i = 0; i < orderedIds.size(); i++) {
            CourseSpot spot = spotMap.get(orderedIds.get(i));
            if (spot != null) {
                spot.updateOrder(i + 1);
                affectedDays.add(spot.getDayNumber());
            }
        }
        return affectedDays;
    }

    public List<CourseSpotResponse> getDaySpots(Long courseId, Integer dayNumber) {
        Course course = findCourseOrThrow(courseId);
        return course.getCourseSpots().stream()
                .filter(cs -> cs.getDayNumber().equals(dayNumber))
                .sorted((a, b) -> a.getSequenceOrder().compareTo(b.getSequenceOrder()))
                .map(this::toCourseSpotResponse)
                .toList();
    }

    @Transactional
    public void updateTravelTimes(Long courseId, Map<Long, Integer> travelTimeUpdates) {
        Course course = findCourseOrThrow(courseId);
        course.getCourseSpots().forEach(spot -> {
            if (travelTimeUpdates.containsKey(spot.getId())) {
                spot.updateTravelTime(travelTimeUpdates.get(spot.getId()));
            }
        });
    }

    // ==================== Private 헬퍼 메서드 ====================

    private Course findCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new CustomException(CourseErrorCode.COURSE_NOT_FOUND));
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    private void validateCourseOwner(Course course, Long userId) {
        if (!userId.equals(course.getUserId())) {
            throw new CustomException(AuthErrorCode.FORBIDDEN_ACCESS);
        }
    }

    // ==================== Entity → DTO 변환 ====================

    private CourseResponse toCourseResponse(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getTitle(),
                course.getStartDate(),
                course.getEndDate(),
                course.getCreatedAt()
        );
    }

    private CourseSpotResponse toCourseSpotResponse(CourseSpot spot) {
        return new CourseSpotResponse(
                spot.getId(),
                spot.getSpotId(),
                spot.getDayNumber(),
                spot.getSequenceOrder(),
                spot.getMemo(),
                spot.getTravelTimeMinutes()
        );
    }

    private CourseChecklistResponse toChecklistResponse(CourseChecklist checklist) {
        return new CourseChecklistResponse(
                checklist.getId(),
                checklist.getContent(),
                checklist.getIsChecked()
        );
    }
}
