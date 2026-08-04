package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompanyDepartmentRequest {

    @Size(max = 255, message = "Department name must be at most 255 characters")
    private String name;

    @Size(max = 100, message = "Department code must be at most 100 characters")
    private String code;

    @Size(max = 2000, message = "Department description must be at most 2000 characters")
    private String description;

    private Boolean isActive;
}
