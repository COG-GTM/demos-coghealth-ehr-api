package com.medchart.ehr.repository;

import com.medchart.ehr.domain.order.ReviewStatus;
import com.medchart.ehr.domain.order.UnmatchedLabResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnmatchedLabResultRepository extends JpaRepository<UnmatchedLabResult, Long> {

    List<UnmatchedLabResult> findByLabResultImportId(Long importId);

    Page<UnmatchedLabResult> findByReviewStatus(ReviewStatus reviewStatus, Pageable pageable);

    long countByReviewStatus(ReviewStatus reviewStatus);
}
