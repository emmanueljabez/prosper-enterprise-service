package com.prosper.prospermentor.service.community;

import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityRealtimeEventItem;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class CommunityRealtimeBroker {
    private static final long STREAM_TIMEOUT_MILLIS = 300_000L;

    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final EmitterFactory emitterFactory;

    public CommunityRealtimeBroker() {
        this(SseEmitter::new);
    }

    CommunityRealtimeBroker(EmitterFactory emitterFactory) {
        this.emitterFactory = emitterFactory;
    }

    public SseEmitter subscribe(UUID viewerId, Set<UUID> visiblePostIds) {
        SseEmitter emitter = emitterFactory.create(STREAM_TIMEOUT_MILLIS);
        Subscription subscription = new Subscription(viewerId, normalize(visiblePostIds), emitter);
        subscriptions.add(subscription);

        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> {
            subscriptions.remove(subscription);
            emitter.complete();
        });
        emitter.onError(error -> subscriptions.remove(subscription));

        sendHandshake(subscription);
        return emitter;
    }

    public void publish(CommunityRealtimeEventItem event) {
        for (Subscription subscription : subscriptions) {
            if (!subscription.canReceive(event)) {
                continue;
            }

            try {
                subscription.emitter().send(SseEmitter.event()
                        .id(event.id().toString())
                        .name(event.type())
                        .data(event));
            } catch (IOException | IllegalStateException e) {
                subscriptions.remove(subscription);
                subscription.emitter().completeWithError(e);
            }
        }
    }

    private Set<UUID> normalize(Set<UUID> ids) {
        return ids == null || ids.isEmpty() ? Set.of() : Set.copyOf(ids);
    }

    private void sendHandshake(Subscription subscription) {
        try {
            subscription.emitter().send(SseEmitter.event()
                    .name("community.stream.connected")
                    .data(Map.of("type", "community.stream.connected")));
        } catch (IOException | IllegalStateException e) {
            subscriptions.remove(subscription);
            subscription.emitter().completeWithError(e);
        }
    }

    interface EmitterFactory {
        SseEmitter create(Long timeoutMillis);
    }

    private record Subscription(UUID viewerId, Set<UUID> visiblePostIds, SseEmitter emitter) {
        boolean canReceive(CommunityRealtimeEventItem event) {
            if (!isViewerEligible(event)) {
                return false;
            }

            if (visiblePostIds.isEmpty() || !event.type().startsWith("community.post.")) {
                return true;
            }

            UUID eventPostId = eventPostId(event);
            return eventPostId != null && visiblePostIds.contains(eventPostId);
        }

        private boolean isViewerEligible(CommunityRealtimeEventItem event) {
            return event.recipientProfileId() == null
                    || viewerId.equals(event.recipientProfileId())
                    || viewerId.equals(event.actorProfileId());
        }

        private UUID eventPostId(CommunityRealtimeEventItem event) {
            if ("POST".equals(event.aggregateType())) {
                return event.aggregateId();
            }

            Map<String, Object> payload = event.payload();
            if (payload == null) {
                return null;
            }
            Object postId = payload.get("postId");
            if (postId instanceof UUID uuid) {
                return uuid;
            }
            if (postId instanceof String value && !value.isBlank()) {
                try {
                    return UUID.fromString(value);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
            return null;
        }
    }
}
