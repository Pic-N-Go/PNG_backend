package com.project.picngo.external;

import com.google.firebase.messaging.FirebaseMessaging;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Disabled("이 테스트는 실제 외부 API를 호출하므로, 깃허브 액션 등 자동화 환경에서 실패할 수 있습니다. 로컬에서 수동으로 찔러볼 때만 주석을 걸어주세요!")
class WeatherClientIntegrationTest {

    @MockitoBean
    private FirebaseMessaging firebaseMessaging;

    @Autowired
    private WeatherClient weatherClient;



    @Test
    @DisplayName("실제 기상청 API를 호출하여 날씨 데이터를 받아온다")
    void 실제_기상청_단기예보_조회() {
        // given (서울시청 위경도)
        Double lat = 37.5665;
        Double lng = 126.9780;
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // when
        List<WeatherForecastResponse> forecasts = weatherClient.getShortTermForecast(lat, lng, today);

        // then
        System.out.println("==================================================");
        System.out.println("기상청 실제 날씨 데이터: " + forecasts);
        System.out.println("==================================================");
        assertThat(forecasts).isNotEmpty();
    }

    @Test
    @DisplayName("실제 일출일몰 API를 호출하여 골든아워를 받아온다")
    void 실제_일출일몰_조회() {
        // given
        Double lat = 37.5665;
        Double lng = 126.9780;
        // Sunrise API는 yyyy-MM-dd 포맷을 권장합니다.
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // when
        GoldenHourResponse goldenHour = weatherClient.getGoldenHour(lat, lng, today);

        // then
        System.out.println("==================================================");
        System.out.println("일출일몰 실제 골든아워 데이터: " + goldenHour);
        System.out.println("==================================================");
        assertThat(goldenHour).isNotNull();
        assertThat(goldenHour.sunriseTime()).isNotBlank();
        assertThat(goldenHour.sunsetTime()).isNotBlank();
    }
}
