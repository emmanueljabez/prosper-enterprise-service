package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.service.SupabaseAuthService;
import com.prosper.prospermentor.service.SupabaseDatabaseDebugService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REST controller for admin-only operations
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final SupabaseAuthService supabaseAuthService;
    private final SupabaseDatabaseDebugService debugService;

    /**
     * List all users with pagination
     */
    @GetMapping("/users")
    public Mono<ResponseEntity<Object>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int perPage) {
        
        return supabaseAuthService.listUsers(page, perPage)
                .map(result -> ResponseEntity.ok((Object) result))
                .onErrorReturn(ResponseEntity.internalServerError().<Object>build());
    }

    /**
     * Get user by ID
     */
    @GetMapping("/users/{userId}")
    public Mono<ResponseEntity<Object>> getUserById(@PathVariable String userId) {
        return supabaseAuthService.getUserById(userId)
                .map(result -> ResponseEntity.ok((Object) result))
                .onErrorReturn(ResponseEntity.notFound().<Object>build());
    }

    /**
     * Create a new user
     */
    @PostMapping("/users")
    public Mono<ResponseEntity<Object>> createUser(@RequestBody CreateUserRequest request) {
        return supabaseAuthService.createUser(
                        request.email(), 
                        request.password(), 
                        request.metadata())
                .map(result -> ResponseEntity.ok((Object) result))
                .onErrorReturn(ResponseEntity.badRequest().<Object>build());
    }

    /**
     * Update user password
     */
    @PutMapping("/users/{userId}/password")
    public Mono<ResponseEntity<Object>> updateUserPassword(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {
        
        String newPassword = request.get("password");
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "Password is required")));
        }
        
        return supabaseAuthService.updateUserPassword(userId, newPassword)
                .map(result -> ResponseEntity.ok((Object) result))
                .onErrorReturn(ResponseEntity.internalServerError().<Object>build());
    }

    /**
     * Delete user
     */
    @DeleteMapping("/users/{userId}")
    public Mono<ResponseEntity<Void>> deleteUser(@PathVariable String userId) {
        return supabaseAuthService.deleteUser(userId)
                .map(result -> ResponseEntity.ok().<Void>build())
                .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    /**
     * Update user metadata
     */
    @PutMapping("/users/{userId}/metadata")
    public Mono<ResponseEntity<Object>> updateUserMetadata(
            @PathVariable String userId,
            @RequestBody Map<String, Object> metadata) {
        
        return supabaseAuthService.updateUserMetadata(userId, metadata)
                .map(result -> ResponseEntity.ok((Object) result))
                .onErrorReturn(ResponseEntity.internalServerError().<Object>build());
    }

    /**
     * Debug database triggers and constraints that might cause user creation issues
     */
    @GetMapping("/debug/database")
    public ResponseEntity<Map<String, String>> debugDatabase() {
        try {
            debugService.runComprehensiveDebug();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Database debugging completed. Check logs for details."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Database debugging failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Test minimal user creation to isolate trigger issues
     */
    @PostMapping("/debug/test-user-creation")
    public Mono<ResponseEntity<Object>> testUserCreation(@RequestBody Map<String, String> request) {
        String testEmail = request.get("email");
        if (testEmail == null || testEmail.trim().isEmpty()) {
            testEmail = "test-" + System.currentTimeMillis() + "@example.com";
        }
        
        String testRole = request.getOrDefault("role", "mentee");
        
        String finalTestEmail = testEmail;
        return supabaseAuthService.createMinimalUser(finalTestEmail, "TestPassword123!", testRole)
                .map(result -> ResponseEntity.ok((Object) Map.of(
                    "status", "success",
                    "message", "Test user created successfully",
                    "user", result
                )))
                .onErrorReturn(ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Test user creation failed"
                )));
    }

    /**
     * Request record for creating users
     */
    public record CreateUserRequest(
            String email,
            String password,
            Map<String, Object> metadata
    ) {}
}

