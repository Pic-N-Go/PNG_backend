package com.project.picngo.common.image.service;

import com.project.picngo.common.image.dto.ImageUploadResult;
import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {

    ImageUploadResult upload(MultipartFile file, String directory);

    String getPresignedUrl(String objectKey);

    void delete(String objectKey);
}
