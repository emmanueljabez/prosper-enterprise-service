package com.prosper.prospermentor.exception;

import com.prosper.prospermentor.dto.SessionBookingEligibility;
import lombok.Getter;

/**
 * Exception thrown when a session booking fails
 * Contains detailed eligibility information including recommended upgrade plans
 */
@Getter
public class SessionBookingException extends IllegalStateException {

    private final SessionBookingEligibility eligibility;

    public SessionBookingException(SessionBookingEligibility eligibility) {
        super(eligibility.getMessage());
        this.eligibility = eligibility;
    }

    public SessionBookingException(String message, SessionBookingEligibility eligibility) {
        super(message);
        this.eligibility = eligibility;
    }
}
