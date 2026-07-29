package com.project.picngo.community.domain;

import com.project.picngo.common.image.dto.PhotoExifInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostImageTest {

    @Test
    @DisplayName("이미지 업로드 시 퍼블리싱 화면에 필요한 EXIF 정보를 저장한다")
    void uploadedImageStoresExifInformation() {
        LocalDateTime takenAt = LocalDateTime.of(2026, 5, 10, 5, 30, 14);
        PhotoExifInfo exif = new PhotoExifInfo(
                35.153386,
                129.118785,
                takenAt,
                "Sony",
                "ILCE-7M4",
                "Sony",
                "FE 24-70mm F2.8 GM",
                "Adobe Lightroom Classic 12.3",
                100,
                "1/500 sec",
                "1/500",
                "f/2.8",
                "24 mm",
                "24 mm",
                "Flash did not fire",
                "Auto white balance",
                "Multi-segment",
                "Manual exposure",
                "1",
                7008,
                4672,
                "sRGB",
                "JPEG",
                "f/2.8",
                "3 m",
                "photographer",
                "copyright",
                "description",
                "caption",
                "DSC03421.JPG",
                8_400_000L
        );

        PostImage image = PostImage.uploaded(1L, "community/1/image.jpg", exif);

        assertEquals("Sony", image.getCameraMake());
        assertEquals("ILCE-7M4", image.getCameraModel());
        assertEquals("Sony", image.getLensMake());
        assertEquals("FE 24-70mm F2.8 GM", image.getLensModel());
        assertEquals("Adobe Lightroom Classic 12.3", image.getSoftware());
        assertEquals("1/500", image.getShutterSpeed());
        assertEquals("24 mm", image.getFocalLength35mm());
        assertEquals("Flash did not fire", image.getFlash());
        assertEquals("Auto white balance", image.getWhiteBalance());
        assertEquals("Multi-segment", image.getMeteringMode());
        assertEquals("Manual exposure", image.getExposureMode());
        assertEquals("sRGB", image.getColorSpace());
        assertEquals("DSC03421.JPG", image.getOriginalFileName());
        assertEquals(8_400_000L, image.getFileSize());
    }
}
