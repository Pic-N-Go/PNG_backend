package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.ReviewPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewPhotoRepository extends JpaRepository<ReviewPhoto, Long> {

    List<ReviewPhoto> findByReviewId(Long reviewId);
    List<ReviewPhoto> findByReview_IdIn(List<Long> reviewIds);
}
