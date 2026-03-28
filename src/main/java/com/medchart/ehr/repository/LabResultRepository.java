package com.medchart.ehr.repository;

import com.medchart.ehr.domain.order.LabResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LabResultRepository extends JpaRepository<LabResult, Long> {

    List<LabResult> findByLabOrderId(Long labOrderId);
}
