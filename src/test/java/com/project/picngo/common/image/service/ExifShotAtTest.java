package com.project.picngo.common.image.service;

import com.project.picngo.external.KakaoAddressClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXIF DateTimeOriginal은 "촬영한 카메라의 벽시계"다. 어떤 존에서 읽든 같은 값이 나와야 한다.
 *
 * 예전 구현은 metadata-extractor의 getDateOriginal()을 쓰고 결과를 systemDefault()로 눕혔다.
 * 그 함수는 오프셋 태그(0x9011)가 없으면 GMT로, 있으면 그 오프셋으로 파싱한다 — 파싱 존과
 * 렌더 존이 따로 놀아서 같은 사진이 기기·서버 타임존에 따라 다른 시각으로 저장됐다.
 *   오프셋 없음(안드로이드)  UTC 서버 05:32 / KST 개발기 14:32
 *   오프셋 +09:00(아이폰)   UTC 서버 전날 20:32 / KST 개발기 05:32
 *
 * 픽스처 두 장은 DateTimeOriginal이 같고 오프셋 태그 유무만 다르다.
 * 아래 테스트는 JVM 기본 존을 UTC로 바꿔놓고 돌린다 — 개발기(KST)에서만 통과하는 걸 막는다.
 */
class ExifShotAtTest {

    private static final LocalDateTime EXPECTED = LocalDateTime.of(2026, 8, 23, 5, 32, 0);

    // ponytail: 주소 변환은 이 테스트의 관심사가 아니다 — mock이 null을 준다
    private final ExifExtractor extractor = new ExifExtractor(Mockito.mock(KakaoAddressClient.class));

    private static MultipartFile load(String name) throws IOException {
        try (InputStream in = ExifShotAtTest.class.getResourceAsStream("/exif/" + name)) {
            assertThat(in).as("픽스처 %s", name).isNotNull();
            return new MockMultipartFile("photo", name, "image/jpeg", in.readAllBytes());
        }
    }

    /** 기본 존을 바꿔서 실행하고 원복한다 — 존에 흔들리지 않는지가 이 테스트의 전부다 */
    private LocalDateTime takenAtIn(String zone, String fixture) throws IOException {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            return extractor.extract(load(fixture)).takenAt();
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("오프셋 태그가 없어도 벽시계 그대로 읽는다 (안드로이드·DSLR)")
    void readsWallClockWithoutOffsetTag() throws IOException {
        assertThat(takenAtIn("UTC", "shot-no-offset.jpg")).isEqualTo(EXPECTED);
        assertThat(takenAtIn("Asia/Seoul", "shot-no-offset.jpg")).isEqualTo(EXPECTED);
    }

    @Test
    @DisplayName("오프셋 태그가 있어도 그 값에 끌려가지 않는다 (아이폰)")
    void ignoresOffsetTag() throws IOException {
        assertThat(takenAtIn("UTC", "shot-with-offset.jpg")).isEqualTo(EXPECTED);
        assertThat(takenAtIn("Asia/Seoul", "shot-with-offset.jpg")).isEqualTo(EXPECTED);
    }

    @Test
    @DisplayName("EXIF가 없으면 null — 출품을 막지 않는다")
    void nullWhenNoExif() throws IOException {
        assertThat(takenAtIn("UTC", "no-exif.jpg")).isNull();
    }
}
