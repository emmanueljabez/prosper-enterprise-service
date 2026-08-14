package com.prosper.prospermentor.service.community;

final class CommunityRealtimeEventNames {
    private CommunityRealtimeEventNames() {
    }

    static String toRealtimeType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return "community.event.created";
        }

        return switch (sourceType) {
            case "COMMUNITY_COMMENT_CREATED" -> "community.post.comment.created";
            case "COMMUNITY_COMMENT_DELETED" -> "community.post.comment.deleted";
            case "COMMUNITY_POST_REACTED", "COMMUNITY_POST_UNREACTED" -> "community.post.reaction.updated";
            case "COMMUNITY_COMMENT_REACTED", "COMMUNITY_COMMENT_UNREACTED" -> "community.post.comment.reaction.updated";
            case "COMMUNITY_USER_BLOCKED", "COMMUNITY_USER_UNBLOCKED" -> "community.connection.updated";
            case "COMMUNITY_POST_CREATED" -> "community.post.created";
            case "COMMUNITY_POST_UPDATED", "COMMUNITY_POST_HIDDEN", "COMMUNITY_POST_UNHIDDEN" -> "community.post.updated";
            case "COMMUNITY_POST_DELETED" -> "community.post.deleted";
            case "COMMUNITY_CONTENT_REPORTED" -> "community.notification.created";
            case "COMMUNITY_NOTIFICATION_PREFERENCES_UPDATED" -> "community.notification.preferences.updated";
            default -> "community." + sourceType
                    .replaceFirst("^COMMUNITY_", "")
                    .toLowerCase()
                    .replace('_', '.');
        };
    }
}
