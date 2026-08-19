package com.project.picngo.inquiry.repository;

import com.project.picngo.inquiry.domain.Inquiry;
import com.project.picngo.inquiry.domain.InquiryStatus;
import com.project.picngo.inquiry.domain.InquiryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    @EntityGraph(attributePaths = {"user", "answeredBy"})
    Page<Inquiry> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "answeredBy"})
    Optional<Inquiry> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"user", "answeredBy"})
    @Query("SELECT i FROM Inquiry i " +
            "LEFT JOIN i.user u " +
            "WHERE (:type IS NULL OR i.type = :type) AND " +
            "(:status IS NULL OR i.status = :status) AND " +
            "(:isResolved IS NULL OR i.isResolved = :isResolved) AND " +
            "(:keyword IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(i.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Inquiry> searchInquiriesForAdmin(
            @Param("type") InquiryType type,
            @Param("status") InquiryStatus status,
            @Param("isResolved") Boolean isResolved,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Inquiry i SET i.isResolved = true, i.status = :resolvedStatus " +
            "WHERE i.status = :answeredStatus AND i.isResolved = false AND i.answeredAt < :threshold")
    int bulkAutoResolveInquiries(
            @Param("answeredStatus") InquiryStatus answeredStatus,
            @Param("resolvedStatus") InquiryStatus resolvedStatus,
            @Param("threshold") LocalDateTime threshold
    );
}
