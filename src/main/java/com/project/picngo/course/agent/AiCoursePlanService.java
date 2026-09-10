package com.project.picngo.course.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.picngo.course.agent.dto.AiCoursePlanRequest;
import com.project.picngo.course.agent.dto.AiCoursePlanResponse;
import com.project.picngo.course.agent.dto.CuratedCourseDraft;
import com.project.picngo.course.agent.dto.CuratedSpotItem;
import com.project.picngo.course.domain.Course;
import com.project.picngo.course.domain.CourseSpot;
import com.project.picngo.course.mq.AiCoursePlanMessage;
import com.project.picngo.course.mq.AiCoursePlanProducer;
import com.project.picngo.course.repository.CourseRepository;
import com.project.picngo.course.repository.CourseSpotRepository;
import com.project.picngo.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCoursePlanService {

    private final LeaderAgent leaderAgent;
    private final CourseRepository courseRepository;
    private final CourseSpotRepository courseSpotRepository;
    private final NotificationService notificationService;
    private final AiCoursePlanProducer aiCoursePlanProducer;
    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final String TASK_KEY_PREFIX = "ai:course:task:";
    private static final Duration TASK_TTL = Duration.ofHours(2);

    /**
     * 비동기 기획 요청을 접수하고 RabbitMQ에 발행합니다.
     */
    public String initiatePlan(Long userId, AiCoursePlanRequest request) {
        String taskId = UUID.randomUUID().toString();
        log.info("🚀 AI 코스 기획 요청 접수: taskId={}, userId={}, prompt='{}'", taskId, userId, request.prompt());

        // Redis에 초기 진행 상태 저장
        saveTaskStatus(taskId, AiCoursePlanResponse.accepted(taskId));

        // RabbitMQ 비동기 큐로 메시지 발행
        AiCoursePlanMessage message = new AiCoursePlanMessage(
                taskId,
                userId,
                request.prompt(),
                request.region(),
                request.targetDate() != null ? request.targetDate() : LocalDate.now().plusDays(1)
        );
        aiCoursePlanProducer.enqueue(message);

        return taskId;
    }

    /**
     * RabbitMQ 컨슈머에 의해 비동기 백그라운드에서 실행되는 오케스트레이션 및 DB 저장 파이프라인.
     */
    @Transactional
    public void executePlan(AiCoursePlanMessage message) {
        String taskId = message.taskId();
        Long userId = message.userId();
        log.info("⚙️ [AI Worker] 멀티 에이전트 오케스트레이션 실행 시작: taskId={}, userId={}", taskId, userId);

        try {
            // 1. LeaderAgent 오케스트레이션 실행 (의도 분석 -> 스팟 탐색 -> 동선 최적화 -> 날씨/골든아워 -> 큐레이션)
            CuratedCourseDraft draft = leaderAgent.planCourse(message.prompt(), message.region(), message.targetDate());

            // 2. Course Entity 생성 및 DB 저장
            Course course = Course.builder()
                    .userId(userId)
                    .title(draft.title())
                    .startDate(message.targetDate())
                    .endDate(message.targetDate())
                    .build();
            Course savedCourse = courseRepository.save(course);

            // 3. CourseSpot Entity 생성 및 저장
            for (CuratedSpotItem item : draft.spots()) {
                CourseSpot spot = CourseSpot.builder()
                        .course(savedCourse)
                        .spotId(item.spotId())
                        .dayNumber(1)
                        .sequenceOrder(item.sequenceOrder())
                        .memo(item.photographyTip())
                        .travelTimeMinutes(item.estimatedTravelMinutes())
                        .build();
                courseSpotRepository.save(spot);
            }

            log.info("💾 [AI Worker] 코스 DB 저장 완료: courseId={}, title='{}', 스팟 {}개",
                    savedCourse.getId(), savedCourse.getTitle(), draft.spots().size());

            // 4. Redis 상태 갱신 (COMPLETED)
            AiCoursePlanResponse completedResponse = AiCoursePlanResponse.completed(
                    taskId,
                    savedCourse.getId(),
                    savedCourse.getTitle()
            );
            saveTaskStatus(taskId, completedResponse);

            // 5. 알림 생성 및 FCM 푸시 발송
            notificationService.sendPushNotification(
                    userId,
                    "AI_COURSE_PLAN",
                    "AI 맞춤형 출사 코스 완성!",
                    String.format("'%s' 코스가 성공적으로 기획되었습니다. 지금 바로 확인해보세요!", savedCourse.getTitle()),
                    "/courses/" + savedCourse.getId()
            );

        } catch (Exception e) {
            log.error("❌ [AI Worker] 코스 기획 중 장애 발생: taskId={}, error={}", taskId, e.getMessage(), e);
            saveTaskStatus(taskId, AiCoursePlanResponse.failed(taskId, "코스 기획 도중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    public AiCoursePlanResponse getTaskStatus(String taskId) {
        String json = redisTemplate.opsForValue().get(TASK_KEY_PREFIX + taskId);
        if (json == null) {
            return AiCoursePlanResponse.failed(taskId, "존재하지 않거나 만료된 작업 ID입니다.");
        }
        try {
            return objectMapper.readValue(json, AiCoursePlanResponse.class);
        } catch (Exception e) {
            return AiCoursePlanResponse.failed(taskId, "상태 조회 실패");
        }
    }

    private void saveTaskStatus(String taskId, AiCoursePlanResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(TASK_KEY_PREFIX + taskId, json, TASK_TTL);
        } catch (Exception e) {
            log.warn("Redis 작업 상태 저장 실패: taskId={}, error={}", taskId, e.getMessage());
        }
    }
}
