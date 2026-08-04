package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.ProgramMentor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProgramMentorRepository extends JpaRepository<ProgramMentor, ProgramMentor.ProgramMentorId> {

    @Query("SELECT pm.mentorId FROM ProgramMentor pm WHERE pm.programId = :programId")
    List<UUID> findMentorIdsByProgramId(@Param("programId") UUID programId);

    @Query("SELECT DISTINCT pm.mentorId FROM ProgramMentor pm WHERE pm.programId IN :programIds")
    List<UUID> findMentorIdsByProgramIdIn(@Param("programIds") List<UUID> programIds);
}
