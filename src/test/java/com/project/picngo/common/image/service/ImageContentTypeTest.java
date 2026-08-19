package com.project.picngo.common.image.service;

import com.project.picngo.common.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 업로드 검증은 Content-Type만 믿지 않는다 — 클라이언트가 보내는 값이라 아무 바이트나
 * image/*로 이름표를 달 수 있다. 대신 실제 사진이 막히면 안 되므로, 요즘 폰이 올리는
 * 형식(webp·heic)까지 허용 목록에 들어 있는지 함께 못 박아둔다.
 */
class ImageContentTypeTest {

    private static MultipartFile file(String contentType, byte[] content) {
        return new MockMultipartFile("image", "x.png", contentType, content);
    }

    private static byte[] header(byte[] signature) {
        byte[] header = new byte[12]; // 시그니처를 읽는 데 필요한 최소 길이
        System.arraycopy(signature, 0, header, 0, signature.length);
        return header;
    }

    private static final byte[] PNG = header(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
    private static final byte[] WEBP = header(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'});
    private static final byte[] HEIC = header(new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'});

    @Test
    @DisplayName("image/*가 아닌 Content-Type은 거부한다")
    void rejectsNonImageContentType() {
        assertThatThrownBy(() -> S3ImageStorageService.validateImageFile(file("text/html", PNG)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> S3ImageStorageService.validateImageFile(file(null, PNG)))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("image/*로 위장한 HTML은 바이트를 보고 거부한다")
    void rejectsHtmlDisguisedAsImage() {
        byte[] html = "<html><body><script>alert(1)</script></body></html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> S3ImageStorageService.validateImageFile(file("image/png", html)))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("png·webp·heic는 통과한다 — 요즘 폰 사진이 막히면 안 된다")
    void allowsRealImageFormats() {
        assertThatCode(() -> S3ImageStorageService.validateImageFile(file("image/png", PNG)))
                .doesNotThrowAnyException();
        assertThatCode(() -> S3ImageStorageService.validateImageFile(file("image/webp", WEBP)))
                .doesNotThrowAnyException();
        assertThatCode(() -> S3ImageStorageService.validateImageFile(file("image/heic", HEIC)))
                .doesNotThrowAnyException();
    }
}
