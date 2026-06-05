package com.medchart.ehr.export;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DataExportRepository extends JpaRepository<DataExport, Long> {

    Optional<DataExport> findByExportReference(String exportReference);

    Page<DataExport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<DataExport> findByDeletedFalseAndExpiresAtBefore(LocalDateTime now);

    @Modifying
    @Query("UPDATE DataExport d SET d.deleted = true WHERE d.id = :id")
    void markDeleted(Long id);
}
