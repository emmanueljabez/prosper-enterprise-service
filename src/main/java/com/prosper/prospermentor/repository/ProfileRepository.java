package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Profile entity
 */
@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID>, JpaSpecificationExecutor<Profile> {

    /**
     * Find all profiles by role using native query with type cast
     */
    @Query(value = "SELECT * FROM profiles WHERE role = CAST(:role AS user_role)", nativeQuery = true)
    List<Profile> findAllByRole(@Param("role") String role);

    /**
     * Find profile by email
     */
    Optional<Profile> findByEmail(String email);

    /**
     * Find profile by email without requiring the caller to match stored casing.
     */
    Optional<Profile> findByEmailIgnoreCase(String email);

    /**
     * Find profile by ID with company eagerly fetched
     */
    @Query("SELECT p FROM Profile p LEFT JOIN FETCH p.company WHERE p.id = :id")
    Optional<Profile> findByIdWithCompany(@Param("id") UUID id);

    /**
     * Find profiles by role using native query with type cast
     */
    @Query(value = "SELECT * FROM profiles WHERE role = CAST(:role AS user_role)", nativeQuery = true)
    List<Profile> findByRole(@Param("role") String role);

    /**
     * Find all profiles linked to a company with the company relationship loaded.
     */
    @Query("SELECT p FROM Profile p LEFT JOIN FETCH p.company WHERE p.company.id = :companyId")
    List<Profile> findByCompanyId(@Param("companyId") UUID companyId);

    /**
     * Find profiles by role with pagination using native query with type cast
     */
    @Query(value = "SELECT * FROM profiles WHERE role = CAST(:role AS user_role)",
           countQuery = "SELECT COUNT(*) FROM profiles WHERE role = CAST(:role AS user_role)",
           nativeQuery = true)
    Page<Profile> findByRoleWithPagination(@Param("role") String role, Pageable pageable);

    /**
     * Find profiles by role with optional filters (isVerified and searchTerm) and pagination
     */
    @Query(value = """
        SELECT * FROM profiles
        WHERE role = CAST(:role AS user_role)
        AND (:isVerified IS NULL OR is_verified = :isVerified)
        AND (:searchTerm IS NULL OR
             LOWER(first_name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR
             LOWER(last_name) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
        """,
        countQuery = """
        SELECT COUNT(*) FROM profiles
        WHERE role = CAST(:role AS user_role)
        AND (:isVerified IS NULL OR is_verified = :isVerified)
        AND (:searchTerm IS NULL OR
             LOWER(first_name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR
             LOWER(last_name) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
        """,
        nativeQuery = true)
    Page<Profile> findByRoleWithFilters(
        @Param("role") String role,
        @Param("isVerified") Boolean isVerified,
        @Param("searchTerm") String searchTerm,
        Pageable pageable);

    /**
     * Find public mentor profiles while excluding company-sourced private mentors unless
     * they were already public or have been approved for public listing.
     */
    @Query(value = """
        SELECT * FROM profiles p
        WHERE p.role = CAST(:role AS user_role)
        AND (:isVerified IS NULL OR p.is_verified = :isVerified)
        AND (:searchTerm IS NULL OR
             LOWER(p.first_name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR
             LOWER(p.last_name) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
        AND NOT EXISTS (
            SELECT 1
            FROM company_mentor_pool_memberships cmm
            WHERE cmm.mentor_profile_id = p.id
              AND cmm.membership_status = 'ACTIVE'
              AND cmm.public_listing_preexisting = false
              AND cmm.public_approval_status <> 'APPROVED'
        )
        """,
        countQuery = """
        SELECT COUNT(*) FROM profiles p
        WHERE p.role = CAST(:role AS user_role)
        AND (:isVerified IS NULL OR p.is_verified = :isVerified)
        AND (:searchTerm IS NULL OR
             LOWER(p.first_name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR
             LOWER(p.last_name) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
        AND NOT EXISTS (
            SELECT 1
            FROM company_mentor_pool_memberships cmm
            WHERE cmm.mentor_profile_id = p.id
              AND cmm.membership_status = 'ACTIVE'
              AND cmm.public_listing_preexisting = false
              AND cmm.public_approval_status <> 'APPROVED'
        )
        """,
        nativeQuery = true)
    Page<Profile> findPublicMentorsWithFilters(
            @Param("role") String role,
            @Param("isVerified") Boolean isVerified,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    /**
     * Insert profile with proper enum casting
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO profiles (id, email, first_name, last_name, avatar_url, bio, phone, location, gender, industry, role, is_verified, created_at, updated_at)
        VALUES (:id, :email, :firstName, :lastName, :avatarUrl, :bio, :phone, :location, :gender, :industry, CAST(:role AS user_role), :isVerified, :createdAt, :updatedAt)
        """, nativeQuery = true)
    void insertProfileWithEnumCast(@Param("id") UUID id,
                                  @Param("email") String email,
                                  @Param("firstName") String firstName,
                                  @Param("lastName") String lastName,
                                  @Param("avatarUrl") String avatarUrl,
                                  @Param("bio") String bio,
                                  @Param("phone") String phone,
                                  @Param("location") String location,
                                  @Param("gender") String gender,
                                  @Param("industry") String industry,
                                  @Param("role") String role,
                                  @Param("isVerified") Boolean isVerified,
                                  @Param("createdAt") ZonedDateTime createdAt,
                                  @Param("updatedAt") ZonedDateTime updatedAt);
    
    /**
     * Insert auth.users entry for migration purposes
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO auth.users (id, email, created_at, updated_at, email_confirmed_at, confirmation_sent_at, raw_user_meta_data)
        VALUES (:id, :email, :createdAt, :updatedAt, :createdAt, :createdAt, :metadata::jsonb)
        ON CONFLICT (id) DO NOTHING
        """, nativeQuery = true)
    void insertAuthUser(@Param("id") UUID id,
                       @Param("email") String email,
                       @Param("createdAt") ZonedDateTime createdAt,
                       @Param("updatedAt") ZonedDateTime updatedAt,
                       @Param("metadata") String metadata);
    
    /**
     * Insert auth.users entry for migration (simplified)
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO auth.users (id, email, created_at, updated_at, email_confirmed_at, confirmation_sent_at, raw_user_meta_data)
        VALUES (:id, :email, :createdAt, :updatedAt, :createdAt, :createdAt, :metadata::jsonb)
        ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email
        """, nativeQuery = true)
    void insertAuthUserSimple(@Param("id") UUID id,
                             @Param("email") String email,
                             @Param("createdAt") ZonedDateTime createdAt,
                             @Param("updatedAt") ZonedDateTime updatedAt,
                             @Param("metadata") String metadata);
    
    /**
     * Insert profile for migration purposes (assumes auth.users entry exists)
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO profiles (id, email, first_name, last_name, avatar_url, bio, phone, location, gender, industry, role, is_verified, created_at, updated_at)
        VALUES (:id, :email, :firstName, :lastName, :avatarUrl, :bio, :phone, :location, :gender, :industry, CAST(:role AS user_role), :isVerified, :createdAt, :updatedAt)
        """, nativeQuery = true)
    void insertProfileSimple(@Param("id") UUID id,
                            @Param("email") String email,
                            @Param("firstName") String firstName,
                            @Param("lastName") String lastName,
                            @Param("avatarUrl") String avatarUrl,
                            @Param("bio") String bio,
                            @Param("phone") String phone,
                            @Param("location") String location,
                            @Param("gender") String gender,
                            @Param("industry") String industry,
                            @Param("role") String role,
                            @Param("isVerified") Boolean isVerified,
                            @Param("createdAt") ZonedDateTime createdAt,
                            @Param("updatedAt") ZonedDateTime updatedAt);

    /**
     * Find profiles by industry
     */
    List<Profile> findByIndustry(String industry);

    /**
     * Find profiles by location
     */
    List<Profile> findByLocation(String location);

    /**
     * Find verified profiles
     */
    List<Profile> findByIsVerifiedTrue();

    /**
     * Find profiles with expertise containing specific skill
     */
    @Query(value = "SELECT * FROM profiles p WHERE :skill = ANY(p.expertise)", nativeQuery = true)
    List<Profile> findByExpertiseContaining(@Param("skill") String skill);

    /**
     * Find profiles with interests containing specific interest
     */
    @Query(value = "SELECT * FROM profiles p WHERE :interest = ANY(p.interests)", nativeQuery = true)
    List<Profile> findByInterestsContaining(@Param("interest") String interest);

    /**
     * Count profiles by role using native query with type cast
     */
    @Query(value = "SELECT COUNT(*) FROM profiles WHERE role = CAST(:role AS user_role)", nativeQuery = true)
    long countByRole(@Param("role") String role);

    /**
     * Check if profile exists by email
     */
    boolean existsByEmail(String email);

    /**
     * Check if profile exists by username
     */
    boolean existsByUsername(String username);

    /**
     * Update profile's company_id
     * Uses native query to avoid role casting issues
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE profiles SET company_id = :companyId, updated_at = :updatedAt WHERE id = :profileId", nativeQuery = true)
    int updateCompanyId(@Param("profileId") UUID profileId,
                        @Param("companyId") UUID companyId,
                        @Param("updatedAt") ZonedDateTime updatedAt);

    /**
     * Remove company association from profile
     * Uses native query to avoid role casting issues
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE profiles SET company_id = NULL, updated_at = :updatedAt WHERE id = :profileId", nativeQuery = true)
    int removeCompanyId(@Param("profileId") UUID profileId,
                        @Param("updatedAt") ZonedDateTime updatedAt);
}
