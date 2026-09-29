package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.BadgeAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BadgeAuditEventRepository extends JpaRepository<BadgeAuditEvent, UUID> {
    List<BadgeAuditEvent> findByProfileIdOrderByCreatedAtDesc(UUID profileId);
}
