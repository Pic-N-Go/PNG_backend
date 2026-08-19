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
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.course.dto.TravelTimeResult;
import com.project.picngo.spot.dto.NavigationInfo;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
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
    private final EntityManager entityManager;


    // ==================== 코스 CRUD ====================

    @Transactional
    public CourseResponse createCourse(Long userId, CourseCreateRequest request) {
        validateUserExists(userId);
        validateCourseDates(request.startDate(), request.endDate());

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
                .sorted(java.util.Comparator
                        // 1순위: 완료 여부 (진행예정/진행중: false -> 상단 배치, 완료: true -> 하단 배치)
                        .comparing(Course::isCompleted)
                        // 2순위: 시작 날짜가 빠른 순 (오름차순)
                        .thenComparing(Course::getStartDate, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        // 3순위 (동률 시): 최신 생성 순 (내림차순)
                        .thenComparing(Course::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                )
                .map(this::toCourseResponse)
                .toList();
    }

    public CourseDetailResponse getCourseDetail(Long userId, Long courseId) {
        Course course = findCourseOrThrow(courseId);
        validateCourseOwner(course, userId);

        List<CourseSpotResponse> spots = toCourseSpotResponses(course.getCourseSpots());

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
        validateCourseDates(request.startDate(), request.endDate());

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
    public void syncCourseSpots(Long userId, Long courseId, CourseSpotSyncRequest request) {
        Course course = findCourseOrThrow(courseId);
        validateCourseOwner(course, userId);

        List<CourseSpotSyncItem> requestSpots = request.spots() != null ? request.spots() : List.of();
        validateDaySpotLimits(requestSpots);

        // 코스 자체(제목·날짜)는 건드리지 않고 자식(CourseSpot)만 바꾸는 작업이라,
        // 그냥 두면 Course의 @Version이 오르지 않는다. courseSpots는 mappedBy 쪽(비소유)
        // 컬렉션이고 FK는 자식이 들고 있어서, 추가·삭제·순서변경 어느 경우에도
        // Hibernate가 Course를 "변했다"고 보지 않기 때문이다.
        // 실측으로 확인했다: 세 경우 모두 version 0 → 0 (CourseVersionBehaviorTest).
        //
        // 버전이 안 오르면 낙관적 락은 붙어만 있고 아무것도 막지 못한다. 그래서
        // 커밋 시점에 버전을 강제로 올리도록 명시한다.
        //
        // 검증 뒤에 두는 이유: 요청이 거부될 것이면 버전을 건드릴 이유가 없다.
        // (트랜잭션이 롤백되므로 결과는 같지만, 실패할 요청에 부수효과를 주지 않는 편이 읽기 쉽다.)
        entityManager.lock(course, LockModeType.OPTIMISTIC_FORCE_INCREMENT);

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

        validateNewSpotIdsExist(requestSpots, existingSpotMap);

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

    /**
     * 신규로 추가될 항목의 spotId가 실제 존재하는 스팟인지 확인한다.
     * course_spot.spot_id는 FK가 아니라 DB가 걸러주지 않는다. 검증 없이 저장하면
     * 이름/주소/좌표가 전부 null인 유령 행이 조회할 때마다 응답에 섞여 나가고,
     * 해당 구간 이동시간도 영구히 산출 불가로 남는다. 저장 전에 막는 편이 복구가 쉽다.
     *
     * 기존 항목은 dayNumber/순서/메모만 갱신하고 spotId를 건드리지 않으므로 검사 대상이 아니다.
     */
    private void validateNewSpotIdsExist(List<CourseSpotSyncItem> requestSpots,
                                         Map<Long, CourseSpot> existingSpotMap) {
        List<Long> newSpotIds = requestSpots.stream()
                .filter(item -> item.courseSpotId() == null
                        || !existingSpotMap.containsKey(item.courseSpotId()))
                .map(CourseSpotSyncItem::spotId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (newSpotIds.isEmpty()) {
            return;
        }

        // 개수만 비교하면 어떤 id가 없는지는 모르지만, 엔티티를 EAGER로 끌어오지 않아도 된다.
        if (spotRepository.countByIdIn(newSpotIds) != newSpotIds.size()) {
            throw new CustomException(SpotErrorCode.SPOT_NOT_FOUND);
        }
    }

    public List<CourseSpotResponse> getDaySpots(Long courseId, Integer dayNumber) {
        Course course = findCourseOrThrow(courseId);
        List<CourseSpot> daySpots = course.getCourseSpots().stream()
                .filter(cs -> cs.getDayNumber().equals(dayNumber))
                .sorted((a, b) -> a.getSequenceOrder().compareTo(b.getSequenceOrder()))
                .toList();
        return toCourseSpotResponses(daySpots);
    }

    @Transactional
    public void updateTravelTimes(Long courseId, Map<Long, TravelTimeResult> travelTimeUpdates) {
        Course course = findCourseOrThrow(courseId);
        course.getCourseSpots().forEach(spot -> {
            TravelTimeResult result = travelTimeUpdates.get(spot.getId());
            if (result != null) {
                spot.updateTravelTime(result.minutes(), result.estimated());
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

    private void validateCourseDates(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate != null && endDate != null) {
            if (startDate.isAfter(endDate)) {
                throw new CustomException(CourseErrorCode.INVALID_COURSE_DATE_RANGE);
            }
            long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
            if (days > 15) {
                throw new CustomException(CourseErrorCode.EXCEEDED_MAX_COURSE_DAYS);
            }
        }
    }

    private void validateDaySpotLimits(List<CourseSpotSyncItem> requestSpots) {
        if (requestSpots == null || requestSpots.isEmpty()) return;

        Map<Integer, Long> countByDay = requestSpots.stream()
                .filter(item -> item.dayNumber() != null)
                .collect(Collectors.groupingBy(CourseSpotSyncItem::dayNumber, Collectors.counting()));

        for (Map.Entry<Integer, Long> entry : countByDay.entrySet()) {
            if (entry.getValue() > 10) {
                throw new CustomException(CourseErrorCode.EXCEEDED_MAX_DAY_SPOTS);
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

    private List<CourseSpotResponse> toCourseSpotResponses(List<CourseSpot> courseSpots) {
        if (courseSpots == null || courseSpots.isEmpty()) {
            return List.of();
        }

        List<Long> spotIds = courseSpots.stream()
                .map(CourseSpot::getSpotId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Spot> spotMap = spotRepository.findAllById(spotIds).stream()
                .collect(Collectors.toMap(Spot::getId, spot -> spot));

        return courseSpots.stream()
                .map(cs -> toCourseSpotResponse(cs, spotMap.get(cs.getSpotId())))
                .toList();
    }

    private CourseSpotResponse toCourseSpotResponse(CourseSpot spot, Spot actualSpot) {
        return new CourseSpotResponse(
                spot.getId(),
                spot.getSpotId(),
                actualSpot != null ? actualSpot.getName() : null,
                actualSpot != null ? actualSpot.getAddress() : null,
                actualSpot != null ? actualSpot.getLatitude() : null,
                actualSpot != null ? actualSpot.getLongitude() : null,
                NavigationInfo.of(actualSpot),
                actualSpot != null ? actualSpot.getCategoryNames() : null,
                actualSpot != null ? actualSpot.getThumbnailUrl() : null,
                actualSpot != null ? actualSpot.getPhotogenicScore() : null,
                spot.getDayNumber(),
                spot.getSequenceOrder(),
                spot.getMemo(),
                spot.getTravelTimeMinutes(),
                spot.isTravelTimeEstimated()
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
