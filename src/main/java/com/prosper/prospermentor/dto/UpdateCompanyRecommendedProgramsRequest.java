package com.prosper.prospermentor.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class UpdateCompanyRecommendedProgramsRequest {
    private List<UUID> programIds = new ArrayList<>();
}
