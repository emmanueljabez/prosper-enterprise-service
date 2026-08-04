package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.ReviewAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewAnswerRepository extends JpaRepository<ReviewAnswer, UUID> {

    List<ReviewAnswer> findByReviewRequest_IdOrderBySortOrderAsc(UUID reviewRequestId);

    @Query("""
            SELECT ra
            FROM ReviewAnswer ra
            WHERE ra.reviewRequest.id IN :reviewRequestIds
            ORDER BY ra.reviewRequest.createdAt DESC, ra.sortOrder ASC
            """)
    List<ReviewAnswer> findByReviewRequestIdsOrdered(Collection<UUID> reviewRequestIds);

    void deleteByReviewRequest_Id(UUID reviewRequestId);
}
