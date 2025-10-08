package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Program entity
 */
@Repository
public interface ProgramRepository extends JpaRepository<Program, UUID> {

    /**
     * Find program by name
     */
    Optional<Program> findByName(String name);

    /**
     * Find program by legacy ID
     */
    Optional<Program> findByLegacyId(String legacyId);

    /**
     * Find all programs by status
     */
    List<Program> findByStatus(Program.ProgramStatus status);

    /**
     * Find all programs ordered by orderId
     */
    @Query("SELECT p FROM Program p ORDER BY p.orderId ASC")
    List<Program> findAllOrderedByOrderId();

    /**
     * Find all LIVE programs ordered by orderId
     */
    @Query("SELECT p FROM Program p WHERE p.status = 'LIVE' ORDER BY p.orderId ASC")
    List<Program> findLiveProgramsOrdered();

    /**
     * Find programs by name containing (case insensitive)
     */
    @Query("SELECT p FROM Program p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Program> findByNameContainingIgnoreCase(@Param("name") String name);

    /**
     * Find program by ID with mentors eagerly loaded
     */
    @Query("SELECT p FROM Program p LEFT JOIN FETCH p.programMentors pm LEFT JOIN FETCH pm.mentor WHERE p.id = :id")
    Optional<Program> findByIdWithMentors(@Param("id") UUID id);
}