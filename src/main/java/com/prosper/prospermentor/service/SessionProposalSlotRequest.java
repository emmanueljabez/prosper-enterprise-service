package com.prosper.prospermentor.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionProposalSlotRequest {
    private ZonedDateTime scheduledStart;
    private ZonedDateTime scheduledEnd;
}
