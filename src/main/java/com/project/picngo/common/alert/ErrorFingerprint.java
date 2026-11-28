package com.project.picngo.common.alert;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 500 에러의 "지문". 같은 원인으로 반복되는 에러를 하나로 묶기 위해 쓴다.
 *
 * <p>예외 종류 + 근본 원인 종류 + 우리 코드에서 처음 만나는 위치(클래스.메서드:줄)를 합쳐 해시한다.
 * 메시지는 넣지 않는다 - 메시지에는 요청마다 달라지는 값(ID, 입력값)이 섞여 같은 버그가 다른 지문이 되기 때문이다.
 */
public record ErrorFingerprint(
        String hash,
        String exceptionType,
        String rootCauseType,
        String location,
        List<String> appFrames
) {

    static final String APP_PACKAGE = "com.project.picngo.";
    static final String UNKNOWN_LOCATION = "(우리 코드 밖에서 발생)";
    private static final int MAX_APP_FRAMES = 5;
    private static final int MAX_CAUSE_DEPTH = 20;

    public static ErrorFingerprint of(Throwable e) {
        Throwable root = rootCause(e);
        // 근본 원인 쪽 스택이 실제로 터진 곳을 더 잘 가리킨다. 거기에 우리 코드가 없으면 바깥 예외에서 찾는다.
        List<String> frames = appFrames(root);
        if (frames.isEmpty()) {
            frames = appFrames(e);
        }
        String location = frames.isEmpty() ? UNKNOWN_LOCATION : frames.get(0);
        String hash = sha256(e.getClass().getName() + "|" + root.getClass().getName() + "|" + location).substring(0, 12);
        return new ErrorFingerprint(hash, e.getClass().getSimpleName(), root.getClass().getSimpleName(), location, frames);
    }

    static Throwable rootCause(Throwable e) {
        Throwable current = e;
        // 원인 체인이 서로를 가리키는 경우(순환)에도 멈추도록 깊이를 제한한다.
        for (int depth = 0; depth < MAX_CAUSE_DEPTH && current.getCause() != null && current.getCause() != current; depth++) {
            current = current.getCause();
        }
        return current;
    }

    private static List<String> appFrames(Throwable e) {
        List<String> frames = new ArrayList<>();
        for (StackTraceElement element : e.getStackTrace()) {
            if (element.getClassName().startsWith(APP_PACKAGE)) {
                frames.add(format(element));
                if (frames.size() == MAX_APP_FRAMES) {
                    break;
                }
            }
        }
        return frames;
    }

    private static String format(StackTraceElement element) {
        String className = element.getClassName();
        // 스프링 프록시(CourseService$$SpringCGLIB$$0)나 람다(Foo$1)는 원래 클래스 이름으로 묶는다.
        int proxy = className.indexOf("$$");
        if (proxy > 0) {
            className = className.substring(0, proxy);
        }
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        return simpleName + "." + element.getMethodName() + ":" + element.getLineNumber();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // 모든 JVM이 SHA-256을 제공해야 하므로 여기 올 일은 없다.
            throw new IllegalStateException(e);
        }
    }
}
