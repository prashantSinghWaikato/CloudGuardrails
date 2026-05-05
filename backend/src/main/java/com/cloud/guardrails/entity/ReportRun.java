package com.cloud.guardrails.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String reportType;

    private String reportName;

    private String triggerType;

    private String requestedBy;

    private LocalDateTime periodStart;

    private LocalDateTime periodEnd;

    private String status;

    private String aiProvider;

    @Column(length = 12000)
    private String summaryText;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> summaryData;

    private String emailStatus;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> emailRecipients;

    private LocalDateTime emailedAt;

    @Column(length = 2048)
    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_definition_id")
    private ReportDefinition reportDefinition;
}
