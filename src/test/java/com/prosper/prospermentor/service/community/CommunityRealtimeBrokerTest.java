package com.prosper.prospermentor.service.community;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityRealtimeBrokerTest {
    @Test
    void subscribeImmediatelySendsHandshakeEvent() {
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        CommunityRealtimeBroker broker = new CommunityRealtimeBroker(timeout -> emitter);

        SseEmitter result = broker.subscribe(UUID.randomUUID(), Set.of());

        assertThat(result).isEqualTo(emitter);
        assertThat(emitter.sentData())
                .anySatisfy(data -> assertThat(data.toString()).contains("community.stream.connected"));
    }

    private static class RecordingSseEmitter extends SseEmitter {
        private final List<Object> sentData = new ArrayList<>();

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            for (ResponseBodyEmitter.DataWithMediaType item : builder.build()) {
                sentData.add(item.getData());
            }
        }

        List<Object> sentData() {
            return sentData;
        }
    }
}
