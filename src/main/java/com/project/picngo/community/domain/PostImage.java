package com.project.picngo.community.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.common.image.dto.PhotoExifInfo;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "community_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(name = "post_order")
    private Integer postOrder;

    // 사진 EXIF에 기록된 GPS 위도
    private Double latitude;

    // 사진 EXIF에 기록된 GPS 경도
    private Double longitude;

    // 사진 EXIF의 GPS 좌표를 역지오코딩한 주소
    @Column(length = 255)
    private String address;

    // 사진 원본 촬영 시각
    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    // 카메라 제조사
    @Column(name = "camera_make", length = 100)
    private String cameraMake;

    // 카메라 모델명
    @Column(name = "camera_model", length = 100)
    private String cameraModel;

    // 렌즈 제조사
    @Column(name = "lens_make", length = 100)
    private String lensMake;

    // 렌즈 모델명
    @Column(name = "lens_model", length = 150)
    private String lensModel;

    // 사진을 마지막으로 저장하거나 편집한 소프트웨어
    @Column(name = "software", length = 255)
    private String software;

    // 촬영 감도
    private Integer iso;

    // 실제 센서가 빛에 노출된 시간
    @Column(name = "exposure_time", length = 50)
    private String exposureTime;

    // EXIF APEX 값으로 표현된 셔터 속도
    @Column(name = "shutter_speed", length = 50)
    private String shutterSpeed;

    // 촬영 조리개 F값
    @Column(name = "f_number", length = 50)
    private String fNumber;

    // 실제 렌즈 초점 거리
    @Column(name = "focal_length", length = 50)
    private String focalLength;

    // 35mm 필름 규격으로 환산한 초점 거리
    @Column(name = "focal_length_35mm", length = 50)
    private String focalLength35mm;

    // 플래시 사용 여부와 동작 정보
    @Column(name = "flash", length = 100)
    private String flash;

    // 자동 또는 수동 화이트밸런스 정보
    @Column(name = "white_balance", length = 100)
    private String whiteBalance;

    // 평균, 중앙중점, 다분할 등의 측광 방식
    @Column(name = "metering_mode", length = 100)
    private String meteringMode;

    // 자동, 수동, 브라케팅 등의 노출 방식
    @Column(name = "exposure_mode", length = 100)
    private String exposureMode;

    // 이미지 가로 해상도(픽셀)
    @Column(name = "image_width")
    private Integer imageWidth;

    // 이미지 세로 해상도(픽셀)
    @Column(name = "image_height")
    private Integer imageHeight;

    // sRGB, Adobe RGB 등의 색공간
    @Column(name = "color_space", length = 50)
    private String colorSpace;

    // JPEG, PNG 등의 이미지 파일 형식
    @Column(name = "file_format", length = 50)
    private String fileFormat;

    // 사용자가 업로드한 원본 파일 이름
    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    // 업로드한 원본 파일 크기(바이트)
    @Column(name = "file_size")
    private Long fileSize;

    public static PostImage uploaded(Long ownerId, String objectKey, PhotoExifInfo exif) {
        PostImage image = new PostImage();
        image.ownerId = ownerId;
        image.objectKey = objectKey;
        image.latitude = exif.latitude();
        image.longitude = exif.longitude();
        image.address = exif.address();
        image.takenAt = exif.takenAt();
        image.cameraMake = exif.cameraMake();
        image.cameraModel = exif.cameraModel();
        image.lensMake = exif.lensMake();
        image.lensModel = exif.lensModel();
        image.software = exif.software();
        image.iso = exif.iso();
        image.exposureTime = exif.exposureTime();
        image.shutterSpeed = exif.shutterSpeed();
        image.fNumber = exif.fNumber();
        image.focalLength = exif.focalLength();
        image.focalLength35mm = exif.focalLength35mm();
        image.flash = exif.flash();
        image.whiteBalance = exif.whiteBalance();
        image.meteringMode = exif.meteringMode();
        image.exposureMode = exif.exposureMode();
        image.imageWidth = exif.imageWidth();
        image.imageHeight = exif.imageHeight();
        image.colorSpace = exif.colorSpace();
        image.fileFormat = exif.fileFormat();
        image.originalFileName = exif.fileName();
        image.fileSize = exif.fileSize();
        return image;
    }

    public void changePostOrder(int postOrder){
        if(this.post == null){
            throw new IllegalStateException("게시글에 연결되지 않은 이미지입니다.");
        }
        if(postOrder < 0){
            throw new IllegalArgumentException("이미지 순서는 0 이상이어야 합니다.");
        }
        this.postOrder = postOrder;
    }

    public void attachTo(Post post, int postOrder) {
        if (this.post != null) {
            throw new IllegalStateException("이미 게시글에 연결된 이미지입니다.");
        }
        if (postOrder < 0){
            throw new IllegalArgumentException("이미지 순서는 0 이상이어야 합니다.");
        }
        this.post = post;
        this.postOrder = postOrder;
    }
}
