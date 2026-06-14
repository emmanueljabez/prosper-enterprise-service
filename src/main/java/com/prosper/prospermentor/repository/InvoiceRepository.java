package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByPublicToken(String publicToken);
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    List<Invoice> findByPayerUserIdOrderByCreatedAtDesc(UUID payerUserId);
    List<Invoice> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}
