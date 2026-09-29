package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.BadgeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BadgeTypeRepository extends JpaRepository<BadgeType, UUID> {
    Optional<BadgeType> findBySlugAndStatus(String slug, String status);

    List<BadgeType> findByStatusOrderByDisplayOrderAscNameAsc(String status);

    boolean existsBySlug(String slug);
}
