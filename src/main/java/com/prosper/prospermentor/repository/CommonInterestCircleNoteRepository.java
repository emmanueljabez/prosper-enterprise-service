package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CommonInterestCircleNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommonInterestCircleNoteRepository extends JpaRepository<CommonInterestCircleNote, UUID> {
    List<CommonInterestCircleNote> findByCircle_IdOrderByCreatedAtDesc(UUID circleId);
}
