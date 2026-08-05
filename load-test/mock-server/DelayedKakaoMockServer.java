import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 카카오 로컬 검색 API를 흉내내는 목업 서버. 모든 요청에 5초 지연을 건 뒤
 * 빈 결과({"documents":[]})를 반환한다. 서킷브레이커 부하테스트 전용.
 *
 * 새 의존성 없이 JDK 내장 HttpServer만 쓰므로 컴파일 없이 바로 실행 가능하다:
 *   java load-test/mock-server/DelayedKakaoMockServer.java
 */
public class DelayedKakaoMockServer {

    private static final int PORT = 9999;
    private static final long DELAY_MS = 5000;

    public static void main(String[] args) throws IOException {
        AtomicInteger requestCount = new AtomicInteger(0);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/", exchange -> {
            int n = requestCount.incrementAndGet();
            System.out.printf("[%s] #%d 요청 수신 - %d초 대기 시작 (%s)%n",
                    LocalTime.now(), n, DELAY_MS / 1000, exchange.getRequestURI());

            try {
                Thread.sleep(DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            byte[] body = "{\"documents\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }

            System.out.printf("[%s] #%d 응답 완료%n", LocalTime.now(), n);
        });

        server.start();
        System.out.printf("지연 목업 서버 시작: http://localhost:%d (모든 요청 %d초 지연 후 빈 결과 반환)%n",
                PORT, DELAY_MS / 1000);
    }
}
