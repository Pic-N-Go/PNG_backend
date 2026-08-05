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
 *   - /json              -> 일출일몰 응답. 유일하게 지연 없이 즉시 응답한다
 *   - /v1/directions...  -> 길찾기 응답 (KakaoDirectionsApiResponse)
 *   - ...FcstInfoService -> 기상청 예보 응답 (KmaWeatherApiResponse)
 *   - 그 외              -> 로컬 검색 응답 (KakaoLocalSearchResponse)
 *
 * 일출일몰만 빠르게 응답하는 이유: WeatherClient는 한 클래스지만 기상청과 일출일몰이라는
 * 별개 호스트를 호출하고 서킷도 둘로 나뉘어 있다. 기상청만 느리게 만들면
 * "기상청 서킷은 열렸는데 일출일몰은 멀쩡히 동작하는" 상태를 눈으로 확인할 수 있다.
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
    private static final String KMA_BODY =
            "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL_SERVICE\"},"
                    + "\"body\":{\"dataType\":\"JSON\",\"items\":{\"item\":["
                    + "{\"fcstDate\":\"20260805\",\"fcstTime\":\"1200\",\"category\":\"PTY\",\"fcstValue\":\"0\"},"
                    + "{\"fcstDate\":\"20260805\",\"fcstTime\":\"1200\",\"category\":\"SKY\",\"fcstValue\":\"1\"},"
                    + "{\"fcstDate\":\"20260805\",\"fcstTime\":\"1200\",\"category\":\"TMP\",\"fcstValue\":\"25.5\"}"
                    + "]},\"pageNo\":1,\"numOfRows\":100,\"totalCount\":3}}}";
    private static final String SUNRISE_BODY =
            "{\"results\":{\"sunrise\":\"2026-08-05T20:30:00+00:00\","
                    + "\"sunset\":\"2026-08-05T10:45:00+00:00\"},\"status\":\"OK\"}";

    public static void main(String[] args) throws IOException {
        AtomicInteger requestCount = new AtomicInteger(0);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/", exchange -> {
            int n = requestCount.incrementAndGet();
            String path = exchange.getRequestURI().getPath();

            String kind;
            String responseBody;
            boolean delayed = true;

            if (path.contains("/json")) {
                kind = "일출일몰(정상)";
                responseBody = SUNRISE_BODY;
                delayed = false;   // 이 호스트만 건강한 상태로 둔다
            } else if (path.contains("/directions")) {
                kind = "길찾기";
                responseBody = DIRECTIONS_BODY;
            } else if (path.contains("FcstInfoService")) {
                kind = "기상청";
                responseBody = KMA_BODY;
            } else {
                kind = "로컬검색";
                responseBody = LOCAL_SEARCH_BODY;
            }

            System.out.printf("[%s] #%d 요청 수신 (%s)%s (%s)%n",
                    LocalTime.now(), n, kind,
                    delayed ? " - " + (DELAY_MS / 1000) + "초 대기 시작" : " - 즉시 응답",
                    exchange.getRequestURI());

            if (delayed) {
                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }

            System.out.printf("[%s] #%d 응답 완료 (%s)%n", LocalTime.now(), n, kind);
        });

        server.start();
        System.out.printf("지연 목업 서버 시작: http://localhost:%d%n", PORT);
        System.out.printf("  /json           -> 일출일몰 응답 (지연 없음, 건강한 서비스 역할)%n");
        System.out.printf("  /v1/directions  -> 길찾기 응답 (%d초 지연)%n", DELAY_MS / 1000);
        System.out.printf("  *FcstInfoService -> 기상청 응답 (%d초 지연)%n", DELAY_MS / 1000);
        System.out.printf("  그 외            -> 로컬검색 응답 (%d초 지연)%n", DELAY_MS / 1000);
    }
}
