package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.SessionSupportRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SessionSupportRequestRepository extends JpaRepository<SessionSupportRequest, UUID> {
}
