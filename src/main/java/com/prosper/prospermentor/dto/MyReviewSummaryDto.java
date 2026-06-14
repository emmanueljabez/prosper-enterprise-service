package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyReviewSummaryDto {
    private long totalReviews;
    private long actionRequired;
    private long awaitingReveal;
    private long revealed;
    private long expired;
    private long deliveryIssues;
}
