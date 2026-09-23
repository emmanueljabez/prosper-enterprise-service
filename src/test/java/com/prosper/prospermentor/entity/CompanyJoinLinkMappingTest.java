package com.prosper.prospermentor.entity;

import jakarta.persistence.GeneratedValue;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyJoinLinkMappingTest {

    @Test
    void id_shouldBeApplicationAssignedBecauseTokenUsesIdBeforePersist() throws Exception {
        Field idField = CompanyJoinLink.class.getDeclaredField("id");
        CompanyJoinLink link = new CompanyJoinLink();
        UUID id = UUID.randomUUID();

        link.setId(id);

        assertThat(idField.getAnnotation(GeneratedValue.class)).isNull();
        assertThat(link.getId()).isEqualTo(id);
    }
}
