package com.project.picngo.community.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ImageErrorCode;
import com.project.picngo.common.image.dto.ImageUploadResult;
import com.project.picngo.common.image.dto.PhotoExifInfo;
import com.project.picngo.common.image.service.ExifExtractor;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.community.domain.PostImage;
import com.project.picngo.community.dto.ImageUploadResponse;
import com.project.picngo.community.repository.PostImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostImageService {

    private static final int MAX_IMAGE_COUNT = 10;

    private final ExifExtractor exifExtractor;
    private final ImageStorageService imageStorageService;
    private final PostImageRepository imageRepository;

    @Transactional
    public List<ImageUploadResponse> upload(Long userId, List<MultipartFile> files) {
        validateFiles(files);

        List<ImageUploadResponse> responses = new ArrayList<>();
        List<String> uploadedKeys = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                PhotoExifInfo exif = exifExtractor.extract(file);
                ImageUploadResult uploaded = imageStorageService.upload(file, "community/" + userId);
                uploadedKeys.add(uploaded.key());

                PostImage image = imageRepository.save(
                        PostImage.uploaded(userId, uploaded.key(), exif)
                );
                responses.add(new ImageUploadResponse(image.getId(), uploaded.url(), exif));
            }

            imageRepository.flush();
            return responses;
        } catch (RuntimeException exception) {
            deleteUploadedImages(uploadedKeys);
            throw exception;
        }
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new CustomException(ImageErrorCode.IMAGE_FILE_EMPTY);
        }
        if (files.size() > MAX_IMAGE_COUNT) {
            throw new CustomException(ImageErrorCode.IMAGE_FILE_TOO_MANY);
        }
        if (files.stream().anyMatch(file -> file == null || file.isEmpty())) {
            throw new CustomException(ImageErrorCode.IMAGE_FILE_EMPTY);
        }
    }

    private void deleteUploadedImages(List<String> uploadedKeys) {
        for (String uploadedKey : uploadedKeys) {
            try {
                imageStorageService.delete(uploadedKey);
            } catch (RuntimeException cleanupException) {
                log.warn(
                        "커뮤니티 이미지 업로드 실패 후 S3 정리에 실패했습니다. key={}",
                        uploadedKey,
                        cleanupException
                );
            }
        }
    }
}
