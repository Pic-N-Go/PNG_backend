package com.project.picngo.common.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorFingerprintTest {

    @Test
    @DisplayName("같은 위치에서 같은 예외가 나면 메시지가 달라도 지문이 같다")
    void sameSiteSameFingerprint() {
        ErrorFingerprint first = ErrorFingerprint.of(failAt("userId=1"));
        ErrorFingerprint second = ErrorFingerprint.of(failAt("userId=999"));

        assertThat(first.hash()).isEqualTo(second.hash());
    }

    @Test
    @DisplayName("다른 위치에서 난 예외는 지문이 다르다")
    void differentSiteDifferentFingerprint() {
        ErrorFingerprint first = ErrorFingerprint.of(failAt("x"));
        ErrorFingerprint second = ErrorFingerprint.of(failElsewhere());

        assertThat(first.hash()).isNotEqualTo(second.hash());
    }

    @Test
    @DisplayName("위치는 우리 코드에서 처음 만나는 클래스.메서드:줄로 표시한다")
    void locationPointsToAppCode() {
        ErrorFingerprint fingerprint = ErrorFingerprint.of(failAt("x"));

        assertThat(fingerprint.location()).startsWith("ErrorFingerprintTest.failAt:");
        assertThat(fingerprint.appFrames()).isNotEmpty().hasSizeLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("감싸진 예외는 근본 원인의 종류와 위치를 쓴다")
    void usesRootCause() {
        RuntimeException wrapped = new RuntimeException("wrapper", failAt("x"));

        ErrorFingerprint fingerprint = ErrorFingerprint.of(wrapped);

        assertThat(fingerprint.exceptionType()).isEqualTo("RuntimeException");
        assertThat(fingerprint.rootCauseType()).isEqualTo("IllegalStateException");
        assertThat(fingerprint.location()).startsWith("ErrorFingerprintTest.failAt:");
    }

    @Test
    @DisplayName("우리 코드가 스택에 없어도 실패하지 않고 자리표시 위치를 쓴다")
    void noAppFrames() {
        RuntimeException outside = new RuntimeException("outside");
        outside.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("org.example.Library", "call", "Library.java", 10)
        });

        ErrorFingerprint fingerprint = ErrorFingerprint.of(outside);

        assertThat(fingerprint.location()).isEqualTo(ErrorFingerprint.UNKNOWN_LOCATION);
        assertThat(fingerprint.appFrames()).isEmpty();
        assertThat(fingerprint.hash()).hasSize(12);
    }

    @Test
    @DisplayName("스프링 프록시 클래스 이름은 원래 클래스 이름으로 묶는다")
    void stripsProxySuffix() {
        RuntimeException proxied = new RuntimeException("proxied");
        proxied.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.project.picngo.course.service.CourseService$$SpringCGLIB$$0", "update", "CourseService.java", 42)
        });

        assertThat(ErrorFingerprint.of(proxied).location()).isEqualTo("CourseService.update:42");
    }

    private static IllegalStateException failAt(String message) {
        try {
            throw new IllegalStateException(message);
        } catch (IllegalStateException e) {
            return e;
        }
    }

    private static IllegalStateException failElsewhere() {
        try {
            throw new IllegalStateException("x");
        } catch (IllegalStateException e) {
            return e;
        }
    }
}
