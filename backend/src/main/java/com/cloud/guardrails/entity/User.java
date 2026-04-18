package com.cloud.guardrails.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String password;

    private String name;

    private String role;

    @ManyToOne
    private Organization organization;

    @ManyToMany
    @JoinTable(
            name = "user_cloud_accounts",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "cloud_account_id")
    )
    private List<CloudAccount> cloudAccounts;
}