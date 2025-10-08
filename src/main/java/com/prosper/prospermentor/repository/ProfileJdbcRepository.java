package com.prosper.prospermentor.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ProfileJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    private final AtomicReference<String> cachedRoleTypeName = new AtomicReference<>();
    private final AtomicReference<Set<String>> cachedRoleLabels = new AtomicReference<>();

    public Optional<UUID> findAuthUserIdByEmail(String email) {
        try {
            UUID id = jdbcTemplate.queryForObject(
                    "select id from auth.users where email = ?",
                    new SingleColumnRowMapper<>(UUID.class),
                    email
            );
            return Optional.ofNullable(id);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean profileExists(UUID id) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from public.profiles where id = ?",
                Integer.class,
                id
        );
        return count != null && count > 0;
    }

    public String getRoleTypeName() {
        String roleType = cachedRoleTypeName.get();
        if (roleType != null) return roleType;
        String typeName = jdbcTemplate.queryForObject(
                "select udt_name from information_schema.columns where table_schema='public' and table_name='profiles' and column_name='role'",
                String.class
        );
        cachedRoleTypeName.set(typeName);
        return typeName;
    }

    public Set<String> getAllowedRoleLabels() {
        Set<String> labels = cachedRoleLabels.get();
        if (labels != null) return labels;
        String typeName = getRoleTypeName();
        List<String> rows = jdbcTemplate.query(
                "select e.enumlabel from pg_type t join pg_enum e on t.oid=e.enumtypid where t.typname = ? order by e.enumsortorder",
                (rs, rowNum) -> rs.getString(1),
                typeName
        );
        Set<String> set = rows.stream().collect(Collectors.toCollection(LinkedHashSet::new));
        cachedRoleLabels.set(set);
        log.info("profiles.role enum type: {} labels: {}", typeName, set);
        return set;
    }

    public String resolveRoleLabelForUserType(String userType) {
        if (userType == null) return getAllowedRoleLabels().iterator().next();
        String normalized = userType.trim().toUpperCase(Locale.ROOT);
        Set<String> allowed = getAllowedRoleLabels();

        // Try direct match
        if (allowed.contains(normalized)) return normalized;

        // Map ADVISOR/ADVISEE to MENTOR/MENTEE if needed
        if (normalized.equals("ADVISOR")) {
            if (allowed.contains("ADVISOR")) return "ADVISOR";
            if (allowed.contains("MENTOR")) return "MENTOR";
        }
        if (normalized.equals("ADVISEE")) {
            if (allowed.contains("ADVISEE")) return "ADVISEE";
            if (allowed.contains("MENTEE")) return "MENTEE";
        }

        // Fallbacks: lowercase
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (allowed.contains(lower)) return lower;
        if (lower.equals("advisor") && allowed.contains("mentor")) return "mentor";
        if (lower.equals("advisee") && allowed.contains("mentee")) return "mentee";

        // Last resort: first allowed label
        return allowed.iterator().next();
    }

    public UUID upsertProfile(UUID id, String email, String firstName, String lastName, String roleLabel,
                              String avatarUrl, String bio, String phone, String location) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(email, "email is required");
        String typeName = getRoleTypeName();

        if (profileExists(id)) {
            jdbcTemplate.update(
                    "update public.profiles set email = ?, first_name = ?, last_name = ?, avatar_url = ?, bio = ?, phone = ?, location = ?, role = CAST(? as " + typeName + "), updated_at = now() where id = ?",
                    email, firstName, lastName, avatarUrl, bio, phone, location, roleLabel, id
            );
            return id;
        }

        jdbcTemplate.update(
                "insert into public.profiles (id, email, first_name, last_name, avatar_url, bio, phone, location, role, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, CAST(? as " + typeName + "), now(), now())",
                id, email, firstName, lastName, avatarUrl, bio, phone, location, roleLabel
        );
        return id;
    }
}




