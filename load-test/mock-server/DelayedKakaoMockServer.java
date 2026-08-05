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
 * 카카오 API를 흉내내는 목업 서버. 모든 요청에 5초 지연을 건 뒤 정상 형태의 응답을
 * 반환한다. 서킷브레이커 부하테스트 전용.
 *
 * 요청 경로에 따라 응답 형태를 달리한다:
 *   - /v1/directions...  -> 길찾기 응답 (KakaoDirectionsApiResponse)
 *   - 그 외              -> 로컬 검색 응답 (KakaoLocalSearchResponse)
 *
 * 응답을 "정상"으로 주는 이유: 지연만으로 서킷이 열리는지 보려는 것이므로,
 * 응답 파싱 실패 같은 다른 요인이 결과에 섞이지 않아야 한다.
 *
 * 새 의존성 없이 JDK 내장 HttpServer만 쓰므로 컴파일 없이 바로 실행 가능하다:
 *   java load-test/mock-server/DelayedKakaoMockServer.java
 */
public class DelayedKakaoMockServer {

    private static final int PORT = 9999;
    private static final long DELAY_MS = 5000;

    private static final String LOCAL_SEARCH_BODY = "{\"documents\":[]}";
    private static final String DIRECTIONS_BODY =
            "{\"routes\":[{\"result_code\":0,\"result_msg\":\"길찾기 성공\","
                    + "\"summary\":{\"distance\":12000,\"duration\":1800}}]}";

    public static void main(String[] args) throws IOException {
        AtomicInteger requestCount = new AtomicInteger(0);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/", exchange -> {
            int n = requestCount.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            boolean isDirections = path.contains("/directions");

            System.out.printf("[%s] #%d 요청 수신 (%s) - %d초 대기 시작 (%s)%n",
                    LocalTime.now(), n, isDirections ? "길찾기" : "로컬검색",
                    DELAY_MS / 1000, exchange.getRequestURI());

            try {
                Thread.sleep(DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            byte[] body = (isDirections ? DIRECTIONS_BODY : LOCAL_SEARCH_BODY)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }

            System.out.printf("[%s] #%d 응답 완료%n", LocalTime.now(), n);
        });

        server.start();
        System.out.printf("지연 목업 서버 시작: http://localhost:%d (모든 요청 %d초 지연)%n",
                PORT, DELAY_MS / 1000);
        System.out.println("  /v1/directions -> 길찾기 응답 / 그 외 -> 로컬검색 응답");
    }
}
