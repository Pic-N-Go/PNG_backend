package com.project.picngo.inquiry.repository;

import com.project.picngo.inquiry.domain.Inquiry;
import com.project.picngo.inquiry.domain.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Page<Inquiry> findByUserId(Long userId, Pageable pageable);

    Optional<Inquiry> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT i FROM Inquiry i WHERE " +
            "(:status IS NULL OR i.status = :status) AND " +
            "(:isResolved IS NULL OR i.isResolved = :isResolved) AND " +
            "(:keyword IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(i.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(i.user.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(i.user.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Inquiry> searchInquiriesForAdmin(
            @Param("status") InquiryStatus status,
            @Param("isResolved") Boolean isResolved,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    java.util.List<Inquiry> findByStatusAndIsResolvedFalseAndAnsweredAtBefore(
            InquiryStatus status,
            java.time.LocalDateTime threshold
    );
}
