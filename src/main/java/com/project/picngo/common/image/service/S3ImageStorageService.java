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

        try {
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
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

    private void validateImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomException(ImageErrorCode.INVALID_IMAGE_FILE);
        }
    }

    private void deleteQuietly(String objectKey) {
        try {
            delete(objectKey);
        } catch (RuntimeException e) {
            log.warn("Presigned URL 생성 실패 후 업로드된 이미지 삭제에 실패했습니다. key={}", objectKey, e);
        }
    }
}
