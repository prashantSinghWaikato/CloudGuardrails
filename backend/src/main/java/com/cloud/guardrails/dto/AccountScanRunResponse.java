package com.cloud.guardrails.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountScanRunResponse {
    private Long id;
    private String startedAt;
    private String completedAt;
    private String status;
    private String message;
    private Integer eventsSeen;
    private Integer eventsIngested;
    private Integer duplicatesSkipped;
    private Integer violationsCreated;
    private Integer postureFindingsCreated;
}
