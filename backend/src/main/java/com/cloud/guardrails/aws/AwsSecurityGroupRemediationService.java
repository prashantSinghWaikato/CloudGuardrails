package com.cloud.guardrails.aws;

import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.entity.Event;
import com.cloud.guardrails.entity.Remediation;
import com.cloud.guardrails.entity.Violation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.IpRange;
import software.amazon.awssdk.services.ec2.model.RevokeSecurityGroupIngressRequest;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AwsSecurityGroupRemediationService {

    private final AwsClientFactory awsClientFactory;

    public void revokeIngress(Remediation remediation) {
        IngressSpec ingressSpec = resolveIngressSpec(remediation);
        CloudAccount account = remediation.getViolation().getCloudAccount();

        try (Ec2Client ec2Client = awsClientFactory.createEc2Client(account)) {
            ec2Client.revokeSecurityGroupIngress(RevokeSecurityGroupIngressRequest.builder()
                    .groupId(ingressSpec.groupId())
                    .ipPermissions(IpPermission.builder()
                            .ipProtocol(ingressSpec.protocol())
                            .fromPort(ingressSpec.fromPort())
                            .toPort(ingressSpec.toPort())
                            .ipRanges(IpRange.builder().cidrIp(ingressSpec.cidr()).build())
                            .build())
                    .build());
        }
    }

    public boolean isIngressRevoked(Remediation remediation) {
        IngressSpec ingressSpec = resolveIngressSpec(remediation);
        CloudAccount account = remediation.getViolation().getCloudAccount();

        try (Ec2Client ec2Client = awsClientFactory.createEc2Client(account)) {
            var response = ec2Client.describeSecurityGroups(DescribeSecurityGroupsRequest.builder()
                    .groupIds(ingressSpec.groupId())
                    .build());

            return response.securityGroups().stream()
                    .flatMap(group -> group.ipPermissions().stream())
                    .noneMatch(permission -> matches(permission, ingressSpec));
        }
    }

    private IngressSpec resolveIngressSpec(Remediation remediation) {
        Violation violation = remediation.getViolation();
        if (violation == null || violation.getCloudAccount() == null) {
            throw new IllegalArgumentException("Cloud account is required for remediation");
        }

        CloudAccount account = violation.getCloudAccount();
        Event event = violation.getEvent();
        Map<String, Object> payload = event != null ? event.getPayload() : Map.of();

        String groupId = requireString(violation.getResourceId(), "Security group resourceId is required");
        Integer fromPort = readInteger(payload, List.of("port", "fromPort", "requestParameters.fromPort"));
        Integer toPort = readInteger(payload, List.of("toPort", "requestParameters.toPort"));
        String cidr = readString(payload, List.of("cidr", "cidrIp", "requestParameters.cidr", "requestParameters.cidrIp"));
        String protocol = readString(payload, List.of("protocol", "ipProtocol", "requestParameters.protocol", "requestParameters.ipProtocol"));

        if (fromPort == null) {
            throw new IllegalArgumentException("Ingress port is required for security group remediation");
        }
        if (toPort == null) {
            toPort = fromPort;
        }
        if (cidr == null || cidr.isBlank()) {
            throw new IllegalArgumentException("CIDR block is required for security group remediation");
        }
        if (protocol == null || protocol.isBlank()) {
            protocol = "tcp";
        }

        return new IngressSpec(groupId, fromPort, toPort, cidr, protocol);
    }

    private boolean matches(IpPermission permission, IngressSpec ingressSpec) {
        if (permission == null) {
            return false;
        }

        boolean sameProtocol = ingressSpec.protocol().equalsIgnoreCase(permission.ipProtocol());
        boolean sameFromPort = permission.fromPort() != null && permission.fromPort().equals(ingressSpec.fromPort());
        boolean sameToPort = permission.toPort() != null && permission.toPort().equals(ingressSpec.toPort());
        boolean sameCidr = permission.ipRanges().stream()
                .map(IpRange::cidrIp)
                .anyMatch(cidr -> ingressSpec.cidr().equalsIgnoreCase(cidr));

        return sameProtocol && sameFromPort && sameToPort && sameCidr;
    }

    private String requireString(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String readString(Map<String, Object> payload, List<String> keys) {
        for (String key : keys) {
            Object value = readValue(payload, key);
            if (value != null) {
                String text = String.valueOf(value);
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private Integer readInteger(Map<String, Object> payload, List<String> keys) {
        for (String key : keys) {
            Object value = readValue(payload, key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                return Integer.parseInt(text);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object readValue(Map<String, Object> payload, String path) {
        Object current = payload;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private record IngressSpec(String groupId, Integer fromPort, Integer toPort, String cidr, String protocol) {
    }
}
