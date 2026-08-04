package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.ReviewAlert;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateReviewAlertStatusRequest {

    @NotNull(message = "status is required")
    private ReviewAlert.ReviewAlertStatus status;
}
