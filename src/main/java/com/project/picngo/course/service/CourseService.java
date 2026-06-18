package com.project.picngo.course.service;

import com.project.picngo.course.dto.*;
import com.project.picngo.course.domain.Course;
import com.project.picngo.course.domain.CourseChecklist;
import com.project.picngo.course.domain.CourseSpot;
import com.project.picngo.course.repository.CourseChecklistRepository;
import com.project.picngo.course.repository.CourseRepository;
import com.project.picngo.course.repository.CourseSpotRepository;
import com.project.picngo.external.DirectionsClient;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.CourseErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseSpotRepository courseSpotRepository;
    private final CourseChecklistRepository courseChecklistRepository;
    private final DirectionsClient directionsClient;

    // ==================== 코스 CRUD ====================

    @Transactional
    public CourseResponse createCourse(Long userId, CourseCreateRequest request) {
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

    public CourseDetailResponse getCourseDetail(Long courseId) {
        Course course = findCourseOrThrow(courseId);

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
    public CourseResponse updateCourse(Long courseId, CourseCreateRequest request) {
        Course course = findCourseOrThrow(courseId);
        course.update(request.title(), request.startDate(), request.endDate());
        return toCourseResponse(course);
    }

    @Transactional
    public void deleteCourse(Long courseId) {
        Course course = findCourseOrThrow(courseId);
        courseRepository.delete(course);
    }

    // ==================== 코스 스팟 관리 ====================

    @Transactional
    public CourseSpotResponse addCourseSpot(Long courseId, CourseSpotAddRequest request) {
        Course course = findCourseOrThrow(courseId);

        // 이동 시간 캐싱: 이전 스팟이 존재하면 DirectionsClient로 이동 시간 계산
        Integer travelTime = calculateTravelTime(course, request);

        CourseSpot courseSpot = CourseSpot.builder()
                .course(course)
                .spotId(request.spotId())
                .dayNumber(request.dayNumber())
                .sequenceOrder(request.sequenceOrder())
                .memo(request.memo())
                .travelTimeMinutes(travelTime)
                .build();

        CourseSpot saved = courseSpotRepository.save(courseSpot);
        return toCourseSpotResponse(saved);
    }

    @Transactional
    public void removeCourseSpot(Long courseId, Long spotId) {
        Course course = findCourseOrThrow(courseId);
        course.getCourseSpots().removeIf(cs -> cs.getId().equals(spotId));
    }

    @Transactional
    public void updateSpotOrder(Long courseId, CourseSpotOrderUpdateRequest request) {
        Course course = findCourseOrThrow(courseId);

        // spotId 리스트 순서대로 sequenceOrder를 재배정
        Map<Long, CourseSpot> spotMap = course.getCourseSpots().stream()
                .collect(Collectors.toMap(CourseSpot::getId, cs -> cs));

        List<Long> orderedIds = request.spotIds();
        for (int i = 0; i < orderedIds.size(); i++) {
            CourseSpot spot = spotMap.get(orderedIds.get(i));
            if (spot != null) {
                spot.updateOrder(i + 1);
            }
        }
    }

    // ==================== Private 헬퍼 메서드 ====================

    private Course findCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new CustomException(CourseErrorCode.COURSE_NOT_FOUND));
    }

    /**
     * 코스에 스팟을 추가할 때, 같은 일차(dayNumber)의 이전 스팟이 있으면
     * DirectionsClient를 호출하여 이동 시간을 계산하고 캐싱합니다.
     */
    private Integer calculateTravelTime(Course course, CourseSpotAddRequest request) {
        // 같은 일차의 스팟 중 바로 앞 순서의 스팟을 찾는다
        return course.getCourseSpots().stream()
                .filter(cs -> cs.getDayNumber().equals(request.dayNumber()))
                .filter(cs -> cs.getSequenceOrder() < request.sequenceOrder())
                .max((a, b) -> a.getSequenceOrder().compareTo(b.getSequenceOrder()))
                .map(prevSpot -> {
                    // TODO: prevSpot.getSpotId()와 request.spotId()의 실제 좌표를 조회하여 전달
                    // 현재는 Spot 엔티티의 위경도를 아직 모르므로 더미 좌표로 호출
                    return directionsClient.getTravelTimeMinutes(0.0, 0.0, 0.0, 0.0);
                })
                .orElse(null); // 첫 번째 스팟이면 이동 시간 없음
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
