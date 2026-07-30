package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.ReviewPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewPhotoRepository extends JpaRepository<ReviewPhoto, Long> {

    List<ReviewPhoto> findByReviewId(Long reviewId);
    // 정렬을 명시하지 않으면 순서가 DB 재량이라 화면에서 사진이 뒤섞일 수 있다.
    List<ReviewPhoto> findByReview_IdInOrderByIdAsc(List<Long> reviewIds);

    int countByReviewId(Long reviewId);
}
