package com.project.picngo.community.dto;

import com.project.picngo.common.image.domain.ExifConsentStatus;
import com.project.picngo.community.domain.PostWeather;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

public record PostCreateRequest(
        @NotBlank
        @Size(max = 2000)
        String content,
        @Positive
        Long spotId,
        @NotNull
        LocalTime shootingTime,
        @NotNull
        PostWeather weather,
        @Size(max = 100)
        String cameraModel,
        @Size(max = 150)
        String lensModel,
        @Size(max = 10)
        List<@NotBlank @Size(max = 30) String> tags,
        @NotNull
        ExifConsentStatus technicalExifConsent,
        @NotNull
        ExifConsentStatus locationExifConsent
) {
}
