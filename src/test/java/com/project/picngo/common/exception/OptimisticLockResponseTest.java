package com.project.picngo.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 낙관적 락 충돌이 500이 아니라 409로 나가는지 고정한다.
 *
 * <p>이 매핑이 빠지면 {@code @ExceptionHandler(Exception.class)}로 떨어져 500이 된다.
 * 서버가 고장난 게 아니라 요청이 낡은 것이므로 상태코드가 틀리면 클라이언트가
 * "서버 오류"로 처리하게 되고, 사용자는 무엇을 해야 할지 알 수 없다.
 *
 * <p>핸들러는 붙여놓고 동작을 확인하지 않으면 조용히 안 걸리는 종류의 코드라
 * (실제로 Swagger 인증 설정에서 같은 실수가 있었다) 응답을 직접 확인한다.
 */
class OptimisticLockResponseTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("동시 저장 충돌은 409와 안내 메시지로 응답한다")
    void optimisticLockFailureBecomesConflict() {
        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("Course", 1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(409);
        assertThat(body.code()).isEqualTo("COURSE_MODIFIED_CONCURRENTLY");
        // 사용자가 무엇을 해야 하는지가 메시지에 있어야 한다. 재시도 버튼만 눌러서는
        // 풀리지 않고, 코스를 다시 불러와야 하는 상황이기 때문이다.
        assertThat(body.message()).contains("새로고침");
    }
}
