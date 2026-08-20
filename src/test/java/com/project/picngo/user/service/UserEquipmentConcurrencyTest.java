package com.project.picngo.user.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.UserEquipmentErrorCode;
import com.project.picngo.user.domain.EquipmentType;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.domain.UserEquipment;
import com.project.picngo.user.dto.UserEquipmentCreateRequest;
import com.project.picngo.user.repository.UserEquipmentRepository;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;

@DataJpaTest
@Import(UserEquipmentService.class)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserEquipmentConcurrencyTest {

    @Autowired
    private UserEquipmentService userEquipmentService;

    @Autowired
    private UserEquipmentRepository userEquipmentRepository;

    @Autowired
    private UserRepository userRepository;

    private ExecutorService executorService;
    private Long userId;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(2);

        User user = User.createLocalUser(
                "equipment-concurrency@test.com",
                "encoded-password",
                "equipment-test-user",
                Set.of()
        );
        userId = userRepository.saveAndFlush(user).getId();

        List<UserEquipment> equipments = new ArrayList<>();
        for (int index = 1; index <= 19; index++) {
            equipments.add(UserEquipment.create(
                    userId,
                    EquipmentType.CAMERA,
                    "camera-" + index
            ));
        }
        userEquipmentRepository.saveAllAndFlush(equipments);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
        userEquipmentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("19개에서 동시에 두 개를 등록해도 사용자의 장비는 20개를 초과하지 않는다")
    void doesNotExceedEquipmentLimitUnderConcurrentCreates() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Throwable> first = executorService.submit(
                createEquipmentTask("concurrent-camera-a", ready, start)
        );
        Future<Throwable> second = executorService.submit(
                createEquipmentTask("concurrent-camera-b", ready, start)
        );

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        Throwable firstResult = first.get(10, TimeUnit.SECONDS);
        Throwable secondResult = second.get(10, TimeUnit.SECONDS);

        int successCount = (firstResult == null ? 1 : 0)
                + (secondResult == null ? 1 : 0);
        assertThat(successCount).isEqualTo(1);

        Throwable failure = firstResult != null ? firstResult : secondResult;
        assertThat(failure).isInstanceOf(CustomException.class);
        assertSame(
                UserEquipmentErrorCode.USER_EQUIPMENT_LIMIT_EXCEEDED,
                ((CustomException) failure).getErrorCode()
        );
        assertThat(userEquipmentRepository.countByUserId(userId)).isEqualTo(20L);
    }

    private Callable<Throwable> createEquipmentTask(
            String equipmentName,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                return new IllegalStateException("동시 시작 신호를 기다리는 중 시간이 초과됐습니다.");
            }

            try {
                userEquipmentService.createUserEquipment(
                        userId,
                        new UserEquipmentCreateRequest(EquipmentType.CAMERA, equipmentName)
                );
                return null;
            } catch (Throwable throwable) {
                return throwable;
            }
        };
    }
}
