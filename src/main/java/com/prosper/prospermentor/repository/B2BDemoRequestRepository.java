package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.B2BDemoRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface B2BDemoRequestRepository extends JpaRepository<B2BDemoRequest, UUID> {
    Page<B2BDemoRequest> findAll(Pageable pageable);
}
