package com.cloud.guardrails.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ruleName;

    private String description;

    private String severity;

    private Boolean enabled;

    private Boolean autoRemediation;

    private String remediationAction;
}