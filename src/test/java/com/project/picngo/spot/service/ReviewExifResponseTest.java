package com.project.picngo.spot.service;

import com.project.picngo.common.image.service.ExifExtractor;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.spot.domain.ReviewPhoto;
import com.project.picngo.spot.repository.ReviewPhotoRepository;
import com.project.picngo.spot.repository.ReviewRepository;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ReviewExifResponseTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewPhotoRepository reviewPhotoRepository;
    @Mock private SpotRepository spotRepository;
    @Mock private UserRepository userRepository;
    @Mock private ImageStorageService imageStorageService;
    @Mock private ExifExtractor exifExtractor;

    @InjectMocks private ReviewService reviewService;

    @Test
    @DisplayName("리뷰 EXIF 응답에 저장된 촬영 주소를 포함한다")
    void includesStoredAddress() {
        ReviewPhoto photo = org.mockito.Mockito.mock(ReviewPhoto.class);
        given(reviewRepository.existsById(10L)).willReturn(true);
        given(reviewPhotoRepository.findByReviewIdOrderByIdAsc(10L)).willReturn(List.of(photo));
        given(photo.getLatitude()).willReturn(35.153386);
        given(photo.getLongitude()).willReturn(129.118785);
        given(photo.getAddress()).willReturn("부산광역시 수영구 광안해변로 219");

        var response = reviewService.getReviewExif(10L).images().getFirst();

        assertThat(response.latitude()).isEqualTo(35.153386);
        assertThat(response.longitude()).isEqualTo(129.118785);
        assertThat(response.address()).isEqualTo("부산광역시 수영구 광안해변로 219");
    }
}
