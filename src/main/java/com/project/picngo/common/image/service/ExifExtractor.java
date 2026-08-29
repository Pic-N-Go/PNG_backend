package com.project.picngo.common.image.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.project.picngo.common.image.dto.PhotoExifInfo;
import com.project.picngo.external.KakaoAddressClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Iterator;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExifExtractor {

    private static final DateTimeFormatter EXIF_DATE_TIME = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    private final KakaoAddressClient kakaoAddressClient;

    private static final int TAG_IMAGE_WIDTH = 0x0100;
    private static final int TAG_IMAGE_HEIGHT = 0x0101;
    private static final int TAG_IMAGE_DESCRIPTION = 0x010E;
    private static final int TAG_SOFTWARE = 0x0131;
    private static final int TAG_ARTIST = 0x013B;
    private static final int TAG_COPYRIGHT = 0x8298;
    private static final int TAG_EXPOSURE_TIME = 0x829A;
    private static final int TAG_F_NUMBER = 0x829D;
    private static final int TAG_ISO = 0x8827;
    private static final int TAG_SHUTTER_SPEED = 0x9201;
    private static final int TAG_MAX_APERTURE = 0x9205;
    private static final int TAG_SUBJECT_DISTANCE = 0x9206;
    private static final int TAG_METERING_MODE = 0x9207;
    private static final int TAG_FLASH = 0x9209;
    private static final int TAG_FOCAL_LENGTH = 0x920A;
    private static final int TAG_COLOR_SPACE = 0xA001;
    private static final int TAG_EXIF_IMAGE_WIDTH = 0xA002;
    private static final int TAG_EXIF_IMAGE_HEIGHT = 0xA003;
    private static final int TAG_EXPOSURE_MODE = 0xA402;
    private static final int TAG_WHITE_BALANCE = 0xA403;
    private static final int TAG_DIGITAL_ZOOM_RATIO = 0xA404;
    private static final int TAG_FOCAL_LENGTH_35MM = 0xA405;
    private static final int TAG_LENS_MAKE = 0xA433;
    private static final int TAG_LENS_MODEL = 0xA434;

    public PhotoExifInfo extract(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);

            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            ExifSubIFDDirectory exifDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            ExifIFD0Directory ifd0Directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);

            Double latitude = null;
            Double longitude = null;

            if (gpsDirectory != null && gpsDirectory.getGeoLocation() != null) {
                GeoLocation geoLocation = gpsDirectory.getGeoLocation();
                latitude = geoLocation.getLatitude();
                longitude = geoLocation.getLongitude();
            }

            String address = kakaoAddressClient.coord2Address(latitude, longitude);

            /*
             * EXIF DateTimeOriginal은 정의상 "촬영한 카메라의 벽시계"다. 문자열을 그대로 읽는다.
             *
             * getDateOriginal()을 쓰면 안 된다 — 오프셋 태그(0x9011)가 없으면 GMT로 파싱하고
             * 있으면 그 오프셋으로 파싱하는데, 우리는 결과를 systemDefault()로 다시 눕힌다.
             * 파싱 존과 렌더 존이 따로 놀아서 "05:32 광안리 일출"이 아이폰(+09:00 기록)에서는
             * 전날 20:32로, 안드로이드(오프셋 없음)에서는 14:32로 저장된다.
             */
            LocalDateTime takenAt = parseExifDateTime(string(exifDirectory, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL));

            Integer imageWidth = firstInteger(exifDirectory, ifd0Directory, TAG_EXIF_IMAGE_WIDTH, TAG_IMAGE_WIDTH);
            Integer imageHeight = firstInteger(exifDirectory, ifd0Directory, TAG_EXIF_IMAGE_HEIGHT, TAG_IMAGE_HEIGHT);

            if (imageWidth == null || imageHeight == null) {
                ImageSize imageSize = readImageSize(file);
                if (imageSize != null) {
                    if (imageWidth == null) {
                        imageWidth = imageSize.width();
                    }
                    if (imageHeight == null) {
                        imageHeight = imageSize.height();
                    }
                }
            }

            return new PhotoExifInfo(
                    latitude,
                    longitude,
                    address,
                    takenAt,

                    string(ifd0Directory, ExifIFD0Directory.TAG_MAKE),
                    string(ifd0Directory, ExifIFD0Directory.TAG_MODEL),
                    string(exifDirectory, TAG_LENS_MAKE),
                    string(exifDirectory, TAG_LENS_MODEL),
                    string(ifd0Directory, TAG_SOFTWARE),

                    integer(exifDirectory, TAG_ISO),
                    description(exifDirectory, TAG_EXPOSURE_TIME),
                    description(exifDirectory, TAG_SHUTTER_SPEED),
                    description(exifDirectory, TAG_F_NUMBER),
                    description(exifDirectory, TAG_FOCAL_LENGTH),
                    description(exifDirectory, TAG_FOCAL_LENGTH_35MM),
                    description(exifDirectory, TAG_FLASH),
                    description(exifDirectory, TAG_WHITE_BALANCE),
                    description(exifDirectory, TAG_METERING_MODE),
                    description(exifDirectory, TAG_EXPOSURE_MODE),
                    description(exifDirectory, TAG_DIGITAL_ZOOM_RATIO),

                    imageWidth,
                    imageHeight,
                    description(exifDirectory, TAG_COLOR_SPACE),
                    findTagValue(metadata, "Detected File Type Name", "Detected File Type Long Name"),

                    description(exifDirectory, TAG_MAX_APERTURE),
                    description(exifDirectory, TAG_SUBJECT_DISTANCE),

                    firstString(ifd0Directory, TAG_ARTIST),
                    firstString(ifd0Directory, TAG_COPYRIGHT),
                    firstString(ifd0Directory, TAG_IMAGE_DESCRIPTION),
                    findTagValue(metadata, "Caption/Abstract", "Caption"),

                    file.getOriginalFilename(),
                    file.getSize()
            );
        } catch (Exception e) {
            log.warn("EXIF metadata extraction failed", e);
            return empty(file);
        }
    }

    /** `2026:08:23 05:32:00`. 형식이 어긋나거나 없으면 null — 촬영 시각은 없어도 되는 값이다 */
    private LocalDateTime parseExifDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), EXIF_DATE_TIME);
        } catch (DateTimeParseException e) {
            log.debug("EXIF DateTimeOriginal 파싱 실패: {}", raw);
            return null;
        }
    }

    private PhotoExifInfo empty(MultipartFile file) {
        ImageSize imageSize = readImageSize(file);

        return new PhotoExifInfo(
                null,
                null,
                null,
                null,

                null,
                null,
                null,
                null,
                null,

                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,

                imageSize == null ? null : imageSize.width(),
                imageSize == null ? null : imageSize.height(),
                null,
                null,

                null,
                null,

                null,
                null,
                null,
                null,

                file.getOriginalFilename(),
                file.getSize()
        );
    }

    private ImageSize readImageSize(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {
            if (imageInputStream == null) {
                return null;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream, true, true);
                return new ImageSize(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            log.debug("Image size extraction failed", e);
            return null;
        }
    }

    private String string(Directory directory, int tagType) {
        if (directory == null || !directory.containsTag(tagType)) {
            return null;
        }
        return directory.getString(tagType);
    }

    private String description(Directory directory, int tagType) {
        if (directory == null || !directory.containsTag(tagType)) {
            return null;
        }
        return directory.getDescription(tagType);
    }

    private Integer integer(Directory directory, int tagType) {
        if (directory == null || !directory.containsTag(tagType)) {
            return null;
        }
        return directory.getInteger(tagType);
    }

    private Integer firstInteger(Directory primaryDirectory, Directory fallbackDirectory, int primaryTagType, int fallbackTagType) {
        Integer primaryValue = integer(primaryDirectory, primaryTagType);
        if (primaryValue != null) {
            return primaryValue;
        }
        return integer(fallbackDirectory, fallbackTagType);
    }

    private String firstString(Directory directory, int tagType) {
        String value = string(directory, tagType);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return description(directory, tagType);
    }

    private String findTagValue(Metadata metadata, String... tagNames) {
        for (Directory directory : metadata.getDirectories()) {
            for (Tag tag : directory.getTags()) {
                for (String tagName : tagNames) {
                    if (tag.getTagName().equalsIgnoreCase(tagName)) {
                        return tag.getDescription();
                    }
                }
            }
        }
        return null;
    }

    private record ImageSize(Integer width, Integer height) {
    }
}
