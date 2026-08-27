package com.project.picngo.spot.domain;

import com.project.picngo.common.image.dto.PhotoExifInfo;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("리뷰 사진 고유 ID")
    private Long id;

    @Comment("리뷰 FK")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Comment("S3 Object Key")
    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    private Double latitude;

    private Double longitude;

    @Column(length = 255)
    private String address;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    @Column(name = "camera_make", length = 100)
    private String cameraMake;

    @Column(name = "camera_model", length = 100)
    private String cameraModel;

    @Column(name = "lens_make", length = 100)
    private String lensMake;

    @Column(name = "lens_model", length = 150)
    private String lensModel;

    @Column(name = "software", length = 255)
    private String software;

    private Integer iso;

    @Column(name = "exposure_time", length = 50)
    private String exposureTime;

    @Column(name = "shutter_speed", length = 50)
    private String shutterSpeed;

    @Column(name = "f_number", length = 50)
    private String fNumber;

    @Column(name = "focal_length", length = 50)
    private String focalLength;

    @Column(name = "focal_length_35mm", length = 50)
    private String focalLength35mm;

    @Column(name = "flash", length = 100)
    private String flash;

    @Column(name = "white_balance", length = 100)
    private String whiteBalance;

    @Column(name = "metering_mode", length = 100)
    private String meteringMode;

    @Column(name = "exposure_mode", length = 100)
    private String exposureMode;

    @Column(name = "image_width")
    private Integer imageWidth;

    @Column(name = "image_height")
    private Integer imageHeight;

    @Column(name = "color_space", length = 50)
    private String colorSpace;

    @Column(name = "file_format", length = 50)
    private String fileFormat;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "file_size")
    private Long fileSize;

    public static ReviewPhoto uploaded(Review review, String objectKey, PhotoExifInfo exif){
        ReviewPhoto photo = new ReviewPhoto();
        photo.review = review;
        photo.objectKey = objectKey;
        photo.latitude = exif.latitude();
        photo.longitude = exif.longitude();
        photo.address = exif.address();
        photo.takenAt = exif.takenAt();
        photo.cameraMake = exif.cameraMake();
        photo.cameraModel = exif.cameraModel();
        photo.lensMake = exif.lensMake();
        photo.lensModel = exif.lensModel();
        photo.software = exif.software();
        photo.iso = exif.iso();
        photo.exposureTime = exif.exposureTime();
        photo.shutterSpeed = exif.shutterSpeed();
        photo.fNumber = exif.fNumber();
        photo.focalLength = exif.focalLength();
        photo.focalLength35mm = exif.focalLength35mm();
        photo.flash = exif.flash();
        photo.whiteBalance = exif.whiteBalance();
        photo.meteringMode = exif.meteringMode();
        photo.exposureMode = exif.exposureMode();
        photo.imageWidth = exif.imageWidth();
        photo.imageHeight = exif.imageHeight();
        photo.colorSpace = exif.colorSpace();
        photo.fileFormat = exif.fileFormat();
        photo.originalFileName = exif.fileName();
        photo.fileSize = exif.fileSize();

        return photo;
    }

}
