package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.AccessAuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessAuditLogDto {
    private UUID id;
    private UUID companyId;
    private UUID companyProgramId;
    private String companyProgramName;
    private UUID participantId;
    private String participantName;
    private UUID actorId;
    private String actorName;
    private String actorRole;
    private AccessAuditLog.ResourceType resourceType;
    private UUID resourceId;
    private AccessAuditLog.ActionType action;
    private String reasonCode;
    private LocalDateTime createdAt;
}
