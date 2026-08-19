package com.project.picngo.common.image.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ImageErrorCode;
import com.project.picngo.common.image.dto.ImageUploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3ImageStorageService implements ImageStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.presigned-url-expiration-minutes}")
    private Long presignedUrlExpirationMinutes;

    @Override
    public ImageUploadResult upload(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ImageErrorCode.IMAGE_FILE_EMPTY);
        }
        validateImageFile(file);

        String key = createObjectKey(directory, file.getOriginalFilename());
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(resolveContentType(file))
                .contentLength(file.getSize())
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, file.getSize()));
        } catch (IOException | SdkException e) {
            throw new CustomException(ImageErrorCode.IMAGE_UPLOAD_FAILED);
        }

        try {
            return new ImageUploadResult(key, getPresignedUrl(key));
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
    }

    @Override
    public String getPresignedUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("http")) {
            return objectKey;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpirationMinutes))
                .getObjectRequest(getObjectRequest)
                .build();

        try {
            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (SdkException e) {
            throw new CustomException(ImageErrorCode.IMAGE_PRESIGNED_URL_FAILED);
        }
    }

    @Override
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("http")) {
            return;
        }

        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        try {
            s3Client.deleteObject(deleteObjectRequest);
        } catch (SdkException e) {
            throw new CustomException(ImageErrorCode.IMAGE_DELETE_FAILED);
        }
    }

    private String createObjectKey(String directory, String originalFilename) {
        String normalizedDirectory = directory == null || directory.isBlank()
                ? "images"
                : directory.replaceAll("^/+|/+$", "");
        return normalizedDirectory + "/" + UUID.randomUUID() + extractExtension(originalFilename);
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dotIndex);
    }

    private String resolveContentType(MultipartFile file) {
        return file.getContentType() == null ? "application/octet-stream" : file.getContentType();
    }

    /**
     * Content-Type은 클라이언트가 보내는 값이라 그것만 믿을 수 없다. 앞부분 바이트도 함께 본다.
     *
     * 허용 목록 방식이다. {@code URLConnection.guessContentTypeFromStream}을 쓰지 않는 이유는
     * RIFF로 시작하면 전부 audio/x-wav로 판정해서, RIFF 컨테이너인 webp가 통째로 막히기 때문이다.
     */
    // 인스턴스 상태를 쓰지 않으므로 static — S3 목 없이 검증만 테스트한다(ReviewService.countUploadable과 같은 방식).
    static void validateImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomException(ImageErrorCode.INVALID_IMAGE_FILE);
        }

        byte[] header;
        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(IMAGE_HEADER_LENGTH);
        } catch (IOException e) {
            throw new CustomException(ImageErrorCode.INVALID_IMAGE_FILE);
        }
        if (!looksLikeImage(header)) {
            throw new CustomException(ImageErrorCode.INVALID_IMAGE_FILE);
        }
    }

    /** 시그니처를 읽는 데 필요한 최소 길이. heic/avif는 4~11번째 바이트까지 봐야 한다. */
    private static final int IMAGE_HEADER_LENGTH = 12;

    private static boolean looksLikeImage(byte[] header) {
        if (header.length < IMAGE_HEADER_LENGTH) {
            return false;
        }
        return matches(header, 0, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF)              // jpeg
                || matches(header, 0, (byte) 0x89, 'P', 'N', 'G')                     // png
                || matches(header, 0, 'G', 'I', 'F', '8')                             // gif
                || (matches(header, 0, 'R', 'I', 'F', 'F')
                        && matches(header, 8, 'W', 'E', 'B', 'P'))                    // webp
                || matches(header, 4, 'f', 't', 'y', 'p');                            // heic/heif/avif
    }

    private static boolean matches(byte[] header, int offset, int... signature) {
        for (int i = 0; i < signature.length; i++) {
            if (header[offset + i] != (byte) signature[i]) {
                return false;
            }
        }
        return true;
    }

    private void deleteQuietly(String objectKey) {
        try {
            delete(objectKey);
        } catch (RuntimeException e) {
            log.warn("Presigned URL 생성 실패 후 업로드된 이미지 삭제에 실패했습니다. key={}", objectKey, e);
        }
    }
}
