package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityBlockRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityCommentRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityNotificationPreferencesRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostHiddenRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityReactionRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityReportRequest;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.security.SupabaseUserPrincipal;
import com.prosper.prospermentor.service.community.CommunityMutationService;
import com.prosper.prospermentor.service.community.CommunityReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;

@RestController
@RequestMapping("/api/v1/community")
@RequiredArgsConstructor
public class CommunityController {
    private final CommunityReadService communityReadService;
    private final CommunityMutationService communityMutationService;

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<?>> getFeed(
            Authentication authentication,
            @RequestParam(defaultValue = "latest") String mode,
            @RequestParam(defaultValue = "20") int limit
    ) {
        UUID userId = authenticatedUserId(authentication);
        if (userId == null) {
            return unauthorized();
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Community feed retrieved successfully",
                communityReadService.getFeed(userId, mode, limit)
        ));
    }

    @GetMapping("/recommendations/people")
    public ResponseEntity<ApiResponse<?>> getRecommendedPeople(
            Authentication authentication,
            @RequestParam(defaultValue = "12") int limit
    ) {
        UUID userId = authenticatedUserId(authentication);
        if (userId == null) {
            return unauthorized();
        }

        return ResponseEntity.ok(ApiResponse.success(
                "People recommendations retrieved successfully",
                communityReadService.getRecommendedPeople(userId, limit)
        ));
    }

    @GetMapping("/networks")
    public ResponseEntity<ApiResponse<?>> getNetworkOverview(Authentication authentication) {
        UUID userId = authenticatedUserId(authentication);
        if (userId == null) {
            return unauthorized();
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Network overview retrieved successfully",
                communityReadService.getNetworkOverview(userId)
        ));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<?>> search(
            Authentication authentication,
            @RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return execute(
                authentication,
                userId -> communityReadService.search(userId, query, type, limit),
                "Community search results retrieved successfully",
                HttpStatus.OK
        );
    }

    @GetMapping("/discovery/people")
    public ResponseEntity<ApiResponse<?>> getPeopleDiscovery(
            Authentication authentication,
            @RequestParam(defaultValue = "12") int limit
    ) {
        return execute(
                authentication,
                userId -> communityReadService.getPeopleDiscovery(userId, limit),
                "Community people discovery retrieved successfully",
                HttpStatus.OK
        );
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<?>> getCategories(Authentication authentication) {
        return execute(
                authentication,
                userId -> communityMutationService.getCategories(),
                "Community categories retrieved successfully",
                HttpStatus.OK
        );
    }

    @PostMapping("/posts")
    public ResponseEntity<ApiResponse<?>> createPost(
            Authentication authentication,
            @RequestBody CommunityPostRequest request
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.createPost(userId, request),
                "Community post created successfully",
                HttpStatus.CREATED
        );
    }

    @GetMapping("/posts/saved")
    public ResponseEntity<ApiResponse<?>> getSavedPosts(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return execute(
                authentication,
                userId -> communityReadService.getSavedPosts(userId, limit),
                "Saved community posts retrieved successfully",
                HttpStatus.OK
        );
    }

    @GetMapping("/posts/mine")
    public ResponseEntity<ApiResponse<?>> getMyPosts(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return execute(
                authentication,
                userId -> communityReadService.getMyPosts(userId, limit),
                "My community posts retrieved successfully",
                HttpStatus.OK
        );
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<ApiResponse<?>> getPost(
            Authentication authentication,
            @PathVariable UUID postId
    ) {
        return execute(
                authentication,
                userId -> communityReadService.getPost(userId, postId),
                "Community post retrieved successfully",
                HttpStatus.OK
        );
    }

    @PutMapping("/posts/{postId}")
    public ResponseEntity<ApiResponse<?>> updatePost(
            Authentication authentication,
            @PathVariable UUID postId,
            @RequestBody CommunityPostRequest request
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.updatePost(userId, postId, request),
                "Community post updated successfully",
                HttpStatus.OK
        );
    }

    @PutMapping("/posts/{postId}/hidden")
    public ResponseEntity<ApiResponse<?>> setPostHidden(
            Authentication authentication,
            @PathVariable UUID postId,
            @RequestBody CommunityPostHiddenRequest request
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.setPostHidden(userId, postId, request),
                "Community post hidden state updated successfully",
                HttpStatus.OK
        );
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<ApiResponse<?>> deletePost(
            Authentication authentication,
            @PathVariable UUID postId
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.deletePost(userId, postId),
                "Community post deleted successfully",
                HttpStatus.OK
        );
    }

    @PostMapping("/posts/{postId}/reactions")
    public ResponseEntity<ApiResponse<?>> reactToPost(
            Authentication authentication,
            @PathVariable UUID postId,
            @RequestBody CommunityReactionRequest request
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.reactToPost(userId, postId, request),
                "Community post reaction saved successfully",
                HttpStatus.OK
        );
    }

    @DeleteMapping("/posts/{postId}/reactions/{reactionType}")
    public ResponseEntity<ApiResponse<?>> removeReaction(
            Authentication authentication,
            @PathVariable UUID postId,
            @PathVariable String reactionType
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.removeReaction(userId, postId, reactionType),
                "Community post reaction removed successfully",
                HttpStatus.OK
        );
    }

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<?>> getComments(
            Authentication authentication,
            @PathVariable UUID postId
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.getComments(userId, postId),
                "Community comments retrieved successfully",
                HttpStatus.OK
        );
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<?>> createComment(
            Authentication authentication,
            @PathVariable UUID postId,
            @RequestBody CommunityCommentRequest request
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.createComment(userId, postId, request),
                "Community comment created successfully",
                HttpStatus.CREATED
        );
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<?>> deleteComment(
            Authentication authentication,
            @PathVariable UUID commentId
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.deleteComment(userId, commentId),
                "Community comment deleted successfully",
                HttpStatus.OK
        );
    }

    @PostMapping("/comments/{commentId}/reactions")
    public ResponseEntity<ApiResponse<?>> reactToComment(
            Authentication authentication,
            @PathVariable UUID commentId,
            @RequestBody CommunityReactionRequest request
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.reactToComment(userId, commentId, request),
                "Community comment reaction saved successfully",
                HttpStatus.OK
        );
    }

    @DeleteMapping("/comments/{commentId}/reactions/{reactionType}")
    public ResponseEntity<ApiResponse<?>> removeCommentReaction(
            Authentication authentication,
            @PathVariable UUID commentId,
            @PathVariable String reactionType
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.removeCommentReaction(userId, commentId, reactionType),
                "Community comment reaction removed successfully",
                HttpStatus.OK
        );
    }

    @PostMapping("/posts/{postId}/save")
    public ResponseEntity<ApiResponse<?>> savePost(
            Authentication authentication,
            @PathVariable UUID postId
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.savePost(userId, postId),
                "Community post saved successfully",
                HttpStatus.OK
        );
    }

    @DeleteMapping("/posts/{postId}/save")
    public ResponseEntity<ApiResponse<?>> unsavePost(
            Authentication authentication,
            @PathVariable UUID postId
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.unsavePost(userId, postId),
                "Community post unsaved successfully",
                HttpStatus.OK
        );
    }

    @PostMapping("/blocks")
    public ResponseEntity<ApiResponse<?>> blockUser(
            Authentication authentication,
            @RequestBody CommunityBlockRequest request
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.blockUser(userId, request),
                "Community user blocked successfully",
                HttpStatus.OK
        );
    }

    @DeleteMapping("/blocks/{blockedProfileId}")
    public ResponseEntity<ApiResponse<?>> unblockUser(
            Authentication authentication,
            @PathVariable UUID blockedProfileId
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.unblockUser(userId, blockedProfileId),
                "Community user unblocked successfully",
                HttpStatus.OK
        );
    }

    @PostMapping("/reports")
    public ResponseEntity<ApiResponse<?>> reportContent(
            Authentication authentication,
            @RequestBody CommunityReportRequest request
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.reportContent(userId, request),
                "Community report submitted successfully",
                HttpStatus.CREATED
        );
    }

    @GetMapping("/preferences/notifications")
    public ResponseEntity<ApiResponse<?>> getNotificationPreferences(Authentication authentication) {
        return execute(
                authentication,
                communityMutationService::getNotificationPreferences,
                "Community notification preferences retrieved successfully",
                HttpStatus.OK
        );
    }

    @PutMapping("/preferences/notifications")
    public ResponseEntity<ApiResponse<?>> updateNotificationPreferences(
            Authentication authentication,
            @RequestBody CommunityNotificationPreferencesRequest request
    ) {
        return execute(
                authentication,
                userId -> communityMutationService.updateNotificationPreferences(userId, request),
                "Community notification preferences updated successfully",
                HttpStatus.OK
        );
    }

    private UUID authenticatedUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        String userId = null;

        if (principal instanceof SupabaseUserDetails userDetails) {
            userId = userDetails.getUserId();
        } else if (principal instanceof SupabaseUserPrincipal supabaseUserPrincipal) {
            userId = supabaseUserPrincipal.getUserId();
        }

        if (userId == null || userId.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ResponseEntity<ApiResponse<?>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication required"));
    }

    private ResponseEntity<ApiResponse<?>> execute(
            Authentication authentication,
            Function<UUID, ?> action,
            String successMessage,
            HttpStatus successStatus
    ) {
        UUID userId = authenticatedUserId(authentication);
        if (userId == null) {
            return unauthorized();
        }

        try {
            return ResponseEntity.status(successStatus)
                    .body(ApiResponse.success(successMessage, action.apply(userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }
}
