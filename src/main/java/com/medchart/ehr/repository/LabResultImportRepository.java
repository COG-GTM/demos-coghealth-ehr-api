package com.medchart.ehr.repository;

import com.medchart.ehr.domain.order.ImportStatus;
import com.medchart.ehr.domain.order.LabResultImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LabResultImportRepository extends JpaRepository<LabResultImport, Long> {

    Page<LabResultImport> findByStatusOrderByCreatedAtDesc(ImportStatus status, Pageable pageable);

    Page<LabResultImport> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
