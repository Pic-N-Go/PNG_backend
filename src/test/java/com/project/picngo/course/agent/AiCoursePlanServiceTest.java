package com.project.picngo.course.agent;

import com.project.picngo.course.agent.dto.CuratedCourseDraft;
import com.project.picngo.course.agent.dto.CuratedSpotItem;
import com.project.picngo.course.domain.Course;
import com.project.picngo.course.domain.CourseSpot;
import com.project.picngo.course.mq.AiCoursePlanMessage;
import com.project.picngo.course.mq.AiCoursePlanProducer;
import com.project.picngo.course.repository.CourseRepository;
import com.project.picngo.course.repository.CourseSpotRepository;
import com.project.picngo.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiCoursePlanServiceTest {

    @Mock
    private LeaderAgent leaderAgent;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseSpotRepository courseSpotRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AiCoursePlanProducer aiCoursePlanProducer;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AiCoursePlanService aiCoursePlanService;

    @Test
    @DisplayName("1박2일 코스 기획 완료 시 endDate가 익일로 계산되고 각 스팟의 dayNumber가 정상 저장된다")
    void executePlan_multiDayCourse_savesCorrectDatesAndDayNumbers() {
        // given
        String taskId = "task-123";
        Long userId = 1L;
        LocalDate startDate = LocalDate.of(2026, 9, 12);
        AiCoursePlanMessage message = new AiCoursePlanMessage(taskId, userId, "부산 1박2일 코스", "부산", startDate);

        CuratedSpotItem s1 = new CuratedSpotItem(10L, "해운대", 1, 1, "DAY 1 팁", 0);
        CuratedSpotItem s2 = new CuratedSpotItem(20L, "광안리", 2, 1, "DAY 2 팁", 0);
        CuratedCourseDraft draft = new CuratedCourseDraft("부산 1박 2일 코스", "바다 감성", "개요", 2, List.of(s1, s2));

        given(leaderAgent.planCourse(message.prompt(), message.region(), message.targetDate()))
                .willReturn(draft);

        Course savedCourse = Course.builder()
                .userId(userId)
                .title("부산 1박 2일 코스")
                .startDate(startDate)
                .endDate(startDate.plusDays(1))
                .build();
        ReflectionTestUtils.setField(savedCourse, "id", 100L);

        given(courseRepository.save(any(Course.class))).willReturn(savedCourse);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        aiCoursePlanService.executePlan(message);

        // then
        ArgumentCaptor<Course> courseCaptor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(courseCaptor.capture());
        Course capturedCourse = courseCaptor.getValue();

        assertThat(capturedCourse.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(capturedCourse.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 13)); // 1박 2일이므로 +1일

        ArgumentCaptor<CourseSpot> spotCaptor = ArgumentCaptor.forClass(CourseSpot.class);
        verify(courseSpotRepository, times(2)).save(spotCaptor.capture());
        List<CourseSpot> savedSpots = spotCaptor.getAllValues();

        assertThat(savedSpots.get(0).getDayNumber()).isEqualTo(1);
        assertThat(savedSpots.get(0).getSequenceOrder()).isEqualTo(1);
        assertThat(savedSpots.get(1).getDayNumber()).isEqualTo(2);
        assertThat(savedSpots.get(1).getSequenceOrder()).isEqualTo(1);

        verify(notificationService).sendPushNotification(eq(userId), anyString(), anyString(), anyString(), eq("/courses/100"));
    }
}
