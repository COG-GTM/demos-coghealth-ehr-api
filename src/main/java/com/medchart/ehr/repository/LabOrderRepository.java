package com.medchart.ehr.repository;

import com.medchart.ehr.domain.order.LabOrder;
import com.medchart.ehr.domain.order.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {

    Optional<LabOrder> findByOrderNumber(String orderNumber);

    @Query("SELECT lo FROM LabOrder lo JOIN lo.patient p " +
           "WHERE p.mrn = :mrn AND lo.testCode = :testCode AND lo.status IN :statuses")
    List<LabOrder> findByPatientMrnAndTestCodeAndStatusIn(
            String mrn, String testCode, List<OrderStatus> statuses);

    List<LabOrder> findByStatusIn(List<OrderStatus> statuses);
}
