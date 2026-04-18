package com.cloud.guardrails.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Remediation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;

    private String status;

    private Integer attemptCount;

    private LocalDateTime executedAt;

    private LocalDateTime createdAt;

    private String targetAccountId;

    private String targetResourceId;

    private String lastTriggeredBy;

    private String lastTriggerSource;

    private LocalDateTime lastVerifiedAt;

    private String verificationStatus;

    private String verificationMessage;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> response;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "violation_id", nullable = false)
    private Violation violation;
}
