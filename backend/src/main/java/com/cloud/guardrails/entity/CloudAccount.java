package com.cloud.guardrails.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CloudAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String accountId;

    private String provider;

    private String region;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    private String accessKey;

    private String secretKey;

    private Boolean monitoringEnabled;

    private String activationStatus;

    private String activationMethod;

    private String roleArn;

    private String externalId;

    private LocalDateTime lastActivatedAt;

    private LocalDateTime lastSyncAt;

    private String lastSyncStatus;

    private String lastSyncMessage;
}
