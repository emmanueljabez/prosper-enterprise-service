package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.EmployeeSessionAllocationTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmployeeSessionAllocationTransactionRepository extends JpaRepository<EmployeeSessionAllocationTransaction, UUID> {

    List<EmployeeSessionAllocationTransaction> findByEmployeeSessionAllocation_IdOrderByCreatedAtDesc(UUID employeeSessionAllocationId);
}
