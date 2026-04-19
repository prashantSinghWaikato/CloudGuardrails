package com.cloud.guardrails.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountScanRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cloud_account_id", nullable = false)
    private CloudAccount cloudAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private String status;

    @Column(length = 1024)
    private String message;

    private Integer eventsSeen;

    private Integer eventsIngested;

    private Integer duplicatesSkipped;

    private Integer violationsCreated;

    private Integer postureFindingsCreated;
}
