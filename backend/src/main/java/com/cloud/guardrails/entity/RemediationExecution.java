package com.cloud.guardrails.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemediationExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;

    private String triggerSource;

    private String triggeredBy;

    private Integer attemptNumber;

    private String targetAccountId;

    private String targetResourceId;

    private String status;

    private String verificationStatus;

    @Column(length = 1024)
    private String verificationMessage;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> response;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remediation_id", nullable = false)
    private Remediation remediation;
}
