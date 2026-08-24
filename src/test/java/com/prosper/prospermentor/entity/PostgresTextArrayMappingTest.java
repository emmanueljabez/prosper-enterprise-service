package com.prosper.prospermentor.entity;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresTextArrayMappingTest {

    @Test
    void textArrayColumnsUseJavaArraysInsteadOfJsonBackedLists() {
        List<Class<?>> entities = List.of(
                CompanyMentorInvitation.class,
                CompanyWalkthroughProgress.class,
                MenteeProfile.class,
                MentorProfile.class,
                Profile.class,
                Program.class
        );

        for (Class<?> entity : entities) {
            for (Field field : entity.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                if (column == null || !"text[]".equalsIgnoreCase(column.columnDefinition())) {
                    continue;
                }

                assertThat(field.getType())
                        .as("%s.%s maps PostgreSQL text[] without Hibernate JSON deserialization",
                                entity.getSimpleName(),
                                field.getName())
                        .isEqualTo(String[].class);
            }
        }
    }
}
