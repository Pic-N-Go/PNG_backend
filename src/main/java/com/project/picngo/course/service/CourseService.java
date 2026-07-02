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

        CourseSpot courseSpot = CourseSpot.builder()
                .course(course)
                .spotId(request.spotId())
                .dayNumber(request.dayNumber())
                .sequenceOrder(request.sequenceOrder())
                .memo(request.memo())
                .travelTimeMinutes(null) // 재계산 로직에서 채워짐
                .build();

        CourseSpot saved = courseSpotRepository.save(courseSpot);
        
        // 영속성 컨텍스트에 새 스팟을 추가한 뒤 해당 일차의 이동 시간 재계산
        if (!course.getCourseSpots().contains(saved)) {
            course.getCourseSpots().add(saved);
        }
        recalculateTravelTimesForDay(course, request.dayNumber());
        
        return toCourseSpotResponse(saved);
    }

    @Transactional
    public void removeCourseSpot(Long courseId, Long spotId) {
        Course course = findCourseOrThrow(courseId);
        
        course.getCourseSpots().stream()
                .filter(cs -> cs.getId().equals(spotId))
                .findFirst()
                .ifPresent(spot -> {
                    Integer dayNumber = spot.getDayNumber();
                    course.getCourseSpots().remove(spot);
                    courseSpotRepository.delete(spot);
                    
                    // 중간 장소가 빠졌으므로 동선 재계산
                    recalculateTravelTimesForDay(course, dayNumber);
                });
    }

    @Transactional
    public void updateSpotOrder(Long courseId, CourseSpotOrderUpdateRequest request) {
        Course course = findCourseOrThrow(courseId);

        // spotId 리스트 순서대로 sequenceOrder를 재배정
        Map<Long, CourseSpot> spotMap = course.getCourseSpots().stream()
                .collect(Collectors.toMap(CourseSpot::getId, cs -> cs));

        Integer dayNumber = null;
        List<Long> orderedIds = request.spotIds();
        for (int i = 0; i < orderedIds.size(); i++) {
            CourseSpot spot = spotMap.get(orderedIds.get(i));
            if (spot != null) {
                spot.updateOrder(i + 1);
                if (dayNumber == null) {
                    dayNumber = spot.getDayNumber();
                }
            }
        }
        
        // 순서가 재배정된 해당 일차의 전체 소요시간 재계산
        if (dayNumber != null) {
            recalculateTravelTimesForDay(course, dayNumber);
        }
    }

    // ==================== Private 헬퍼 메서드 ====================

    private Course findCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new CustomException(CourseErrorCode.COURSE_NOT_FOUND));
    }

    /**
     * 특정 일차(dayNumber)의 모든 스팟을 순서대로 정렬한 뒤
     * 처음부터 끝까지 전체 구간의 이동 시간을 동적으로 재계산합니다.
     */
    private void recalculateTravelTimesForDay(Course course, Integer dayNumber) {
        List<CourseSpot> daySpots = course.getCourseSpots().stream()
                .filter(cs -> cs.getDayNumber().equals(dayNumber))
                .sorted((a, b) -> a.getSequenceOrder().compareTo(b.getSequenceOrder()))
                .toList();

        if (daySpots.isEmpty()) return;

        // 첫 번째 장소는 이동 시간이 없으므로 null 처리
        daySpots.get(0).updateTravelTime(null);

        // 두 번째 장소부터 앞 장소와의 이동 시간 계산
        for (int i = 1; i < daySpots.size(); i++) {
            CourseSpot currentSpot = daySpots.get(i);
            
            // TODO: 실제 Spot 엔티티에서 위경도를 가져와야 하나 현재는 도메인이 없으므로 안전한 좌표 하드코딩
            // 서울역 -> 강남역 좌표 사용
            Double startLat = 37.5546;
            Double startLng = 126.9725;
            Double goalLat = 37.4979;
            Double goalLng = 127.0276;

            try {
                Integer travelTime = directionsClient.getTravelTimeMinutes(startLat, startLng, goalLat, goalLng);
                currentSpot.updateTravelTime(travelTime);
            } catch (Exception e) {
                // 에러 발생 시 임의의 30분 처리로 기능 마비 방지
                currentSpot.updateTravelTime(30);
            }
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
