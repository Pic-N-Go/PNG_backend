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
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotRepository;
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
    private final SpotRepository spotRepository;


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

    // ==================== 코스 스팟 관리 (Facade 전용 Internal) ====================

    @Transactional
    public void syncCourseSpots(Long userId, Long courseId, CourseSpotSyncRequest request) {
        Course course = findCourseOrThrow(courseId);
        validateCourseOwner(course, userId);

        List<CourseSpotSyncItem> requestSpots = request.spots();

        // 1. 기존 전체 스팟 조회
        List<CourseSpot> existingSpots = course.getCourseSpots();

        Set<Long> requestSpotIds = requestSpots.stream()
                .map(CourseSpotSyncItem::courseSpotId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // 2. 삭제 대상 처리: 기존 스팟 중 요청에 없는 것은 삭제
        List<CourseSpot> spotsToRemove = existingSpots.stream()
                .filter(cs -> !requestSpotIds.contains(cs.getId()))
                .toList();

        spotsToRemove.forEach(spot -> {
            course.getCourseSpots().remove(spot);
            courseSpotRepository.delete(spot);
        });

        // 3. 업데이트 및 추가 대상 처리
        Map<Long, CourseSpot> existingSpotMap = existingSpots.stream()
                .collect(Collectors.toMap(CourseSpot::getId, cs -> cs));

        for (CourseSpotSyncItem item : requestSpots) {
            if (item.courseSpotId() != null && existingSpotMap.containsKey(item.courseSpotId())) {
                // 기존 스팟 업데이트 (dayNumber, sequenceOrder, memo)
                CourseSpot spot = existingSpotMap.get(item.courseSpotId());
                spot.updateDayNumberOrderAndMemo(item.dayNumber(), item.sequenceOrder(), item.memo());
            } else {
                // 신규 스팟 추가
                CourseSpot newSpot = CourseSpot.builder()
                        .course(course)
                        .spotId(item.spotId())
                        .dayNumber(item.dayNumber())
                        .sequenceOrder(item.sequenceOrder())
                        .memo(item.memo())
                        .travelTimeMinutes(null)
                        .build();
                
                CourseSpot saved = courseSpotRepository.save(newSpot);
                course.getCourseSpots().add(saved);
            }
        }
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

    // ==================== 코스 체크리스트 관리 ====================

    @Transactional
    public CourseChecklistResponse addCourseChecklist(Long userId, Long courseId, CourseChecklistRequest request) {
        Course course = findCourseOrThrow(courseId);
        validateCourseOwner(course, userId);

        CourseChecklist checklist = CourseChecklist.builder()
                .course(course)
                .content(request.content())
                .build();

        CourseChecklist saved = courseChecklistRepository.save(checklist);
        course.getCourseChecklists().add(saved);

        return toChecklistResponse(saved);
    }

    @Transactional
    public CourseChecklistResponse toggleCourseChecklist(Long userId, Long courseId, Long checklistId) {
        Course course = findCourseOrThrow(courseId);
        validateCourseOwner(course, userId);

        CourseChecklist checklist = courseChecklistRepository.findById(checklistId)
                .orElseThrow(() -> new CustomException(CourseErrorCode.COURSE_CHECKLIST_NOT_FOUND));

        if (!checklist.getCourse().getId().equals(courseId)) {
            throw new CustomException(CourseErrorCode.COURSE_CHECKLIST_NOT_FOUND);
        }

        checklist.toggleChecked();
        return toChecklistResponse(checklist);
    }

    @Transactional
    public CourseChecklistResponse updateCourseChecklist(Long userId, Long courseId, Long checklistId, CourseChecklistRequest request) {
        Course course = findCourseOrThrow(courseId);
        validateCourseOwner(course, userId);

        CourseChecklist checklist = courseChecklistRepository.findById(checklistId)
                .orElseThrow(() -> new CustomException(CourseErrorCode.COURSE_CHECKLIST_NOT_FOUND));

        if (!checklist.getCourse().getId().equals(courseId)) {
            throw new CustomException(CourseErrorCode.COURSE_CHECKLIST_NOT_FOUND);
        }

        checklist.updateContent(request.content());
        return toChecklistResponse(checklist);
    }

    @Transactional
    public void deleteCourseChecklist(Long userId, Long courseId, Long checklistId) {
        Course course = findCourseOrThrow(courseId);
        validateCourseOwner(course, userId);

        CourseChecklist checklist = courseChecklistRepository.findById(checklistId)
                .orElseThrow(() -> new CustomException(CourseErrorCode.COURSE_CHECKLIST_NOT_FOUND));

        if (!checklist.getCourse().getId().equals(courseId)) {
            throw new CustomException(CourseErrorCode.COURSE_CHECKLIST_NOT_FOUND);
        }

        course.getCourseChecklists().remove(checklist);
        courseChecklistRepository.delete(checklist);
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
        Spot actualSpot = spotRepository.findById(spot.getSpotId()).orElse(null);
        return new CourseSpotResponse(
                spot.getId(),
                spot.getSpotId(),
                actualSpot != null ? actualSpot.getName() : null,
                actualSpot != null ? actualSpot.getLatitude() : null,
                actualSpot != null ? actualSpot.getLongitude() : null,
                actualSpot != null && actualSpot.getCategory() != null ? actualSpot.getCategory().name() : null,
                actualSpot != null ? actualSpot.getThumbnailUrl() : null,
                actualSpot != null ? actualSpot.getPhotogenicScore() : null,
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
