package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User entity operations with Supabase Auth support
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email address
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by Supabase user ID
     */
    Optional<User> findBySupabaseUserId(String supabaseUserId);

    /**
     * Find all active users
     */
    List<User> findByActiveTrue();

    /**
     * Find users by role
     */
    List<User> findByRole(User.UserRole role);

    /**
     * Find active users by role
     */
    @Query("SELECT u FROM User u WHERE u.active = true AND u.role = :role")
    List<User> findActiveUsersByRole(@Param("role") User.UserRole role);

    /**
     * Check if user exists by email
     */
    boolean existsByEmail(String email);

    /**
     * Check if user exists by Supabase user ID
     */
    boolean existsBySupabaseUserId(String supabaseUserId);

    /**
     * Find users by name containing (case insensitive)
     */
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, ''))) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<User> findByNameContaining(@Param("name") String name);

    /**
     * Find users with Supabase integration
     */
    @Query("SELECT u FROM User u WHERE u.supabaseUserId IS NOT NULL AND u.supabaseUserId != ''")
    List<User> findUsersWithSupabaseAuth();

    /**
     * Find users without Supabase integration
     */
    @Query("SELECT u FROM User u WHERE u.supabaseUserId IS NULL OR u.supabaseUserId = ''")
    List<User> findUsersWithoutSupabaseAuth();

    /**
     * Update last login timestamp
     */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginTime WHERE u.supabaseUserId = :supabaseUserId")
    void updateLastLogin(@Param("supabaseUserId") String supabaseUserId, @Param("loginTime") LocalDateTime loginTime);
}
