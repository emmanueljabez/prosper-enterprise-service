package com.prosper.prospermentor.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.security.Principal;

/**
 * Custom Principal implementation for Supabase authenticated users
 */
@AllArgsConstructor
@Getter
public class SupabaseUserPrincipal implements Principal {
    
    private final String userId;
    private final String email;
    private final String role;

    @Override
    public String getName() {
        return email;
    }

    /**
     * Get the Supabase user ID (UUID)
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Get the user's email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Get the user's role
     */
    public String getRole() {
        return role;
    }
}



