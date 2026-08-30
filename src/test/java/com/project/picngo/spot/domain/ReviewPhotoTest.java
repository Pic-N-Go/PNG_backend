package com.project.picngo.spot.domain;

import com.project.picngo.common.image.dto.PhotoExifInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewPhotoTest {

    @Test
    @DisplayName("리뷰 사진 업로드 시 역지오코딩 주소를 저장한다")
    void uploadedPhotoStoresAddress() {
        Review review = mock(Review.class);
        PhotoExifInfo exif = mock(PhotoExifInfo.class);
        when(exif.address()).thenReturn("부산광역시 수영구 광안해변로 219");

        ReviewPhoto photo = ReviewPhoto.uploaded(review, "reviews/1/image.jpg", exif);

        assertSame(review, photo.getReview());
        assertEquals("부산광역시 수영구 광안해변로 219", photo.getAddress());
    }
}
