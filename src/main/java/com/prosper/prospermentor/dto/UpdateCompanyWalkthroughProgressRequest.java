package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UpdateCompanyWalkthroughProgressRequest {
    @Size(max = 80, message = "Walkthrough version must be 80 characters or fewer")
    private String version;

    private boolean introDismissed;

    @Size(max = 100, message = "Completed task list must contain 100 items or fewer")
    private List<@Size(max = 80, message = "Task ids must be 80 characters or fewer") String> completedTaskIds = new ArrayList<>();

    @Size(max = 100, message = "Completed tour list must contain 100 items or fewer")
    private List<@Size(max = 80, message = "Tour ids must be 80 characters or fewer") String> completedTourIds = new ArrayList<>();
}
