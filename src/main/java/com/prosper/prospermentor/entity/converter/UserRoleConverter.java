package com.prosper.prospermentor.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts user role strings to lowercase to match the PostgreSQL user_role custom enum type.
 * PostgreSQL custom types are case-sensitive and the user_role type expects lowercase values.
 */
@Converter
public class UserRoleConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        // Convert to lowercase for PostgreSQL custom enum type
        return attribute == null ? null : attribute.toLowerCase();
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        // PostgreSQL stores it in lowercase, but we can return as-is or uppercase
        // Returning as-is to maintain consistency with database
        return dbData;
    }
}
