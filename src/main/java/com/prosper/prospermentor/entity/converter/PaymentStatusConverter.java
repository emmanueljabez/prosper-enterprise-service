package com.prosper.prospermentor.entity.converter;

import com.prosper.prospermentor.entity.Payment;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link Payment.PaymentStatus} enums in lowercase to match the Supabase check constraint.
 */
@Converter(autoApply = true)
public class PaymentStatusConverter implements AttributeConverter<Payment.PaymentStatus, String> {

    @Override
    public String convertToDatabaseColumn(Payment.PaymentStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public Payment.PaymentStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Payment.PaymentStatus.valueOf(dbData.toUpperCase());
    }
}
