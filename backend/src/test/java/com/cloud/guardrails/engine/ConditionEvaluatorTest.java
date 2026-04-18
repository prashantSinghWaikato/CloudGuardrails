package com.cloud.guardrails.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionEvaluatorTest {

    private final ConditionEvaluator evaluator = new ConditionEvaluator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void supportsEqualityAndNumericComparison() {
        JsonNode payload = objectMapper.valueToTree(java.util.Map.of(
                "port", 22,
                "cidr", "0.0.0.0/0"
        ));

        assertTrue(evaluator.evaluate("port == 22 && cidr == '0.0.0.0/0'", payload));
        assertTrue(evaluator.evaluate("port >= 22 && port < 23", payload));
        assertFalse(evaluator.evaluate("port > 22", payload));
    }

    @Test
    void supportsNestedFieldsAndExists() {
        JsonNode payload = objectMapper.valueToTree(java.util.Map.of(
                "requestParameters", java.util.Map.of(
                        "bucketName", "demo-bucket",
                        "publicAccess", true
                )
        ));

        assertTrue(evaluator.evaluate("requestParameters.bucketName == 'demo-bucket'", payload));
        assertTrue(evaluator.evaluate("requestParameters.publicAccess == true", payload));
        assertTrue(evaluator.evaluate("requestParameters.bucketName exists", payload));
        assertFalse(evaluator.evaluate("requestParameters.region exists", payload));
    }

    @Test
    void supportsContainsAndOrClauses() {
        JsonNode payload = objectMapper.valueToTree(java.util.Map.of(
                "userIdentity", java.util.Map.of("arn", "arn:aws:iam::123456789012:user/admin"),
                "tags", java.util.List.of("prod", "internet-facing")
        ));

        assertTrue(evaluator.evaluate("userIdentity.arn contains 'admin'", payload));
        assertTrue(evaluator.evaluate("tags contains 'prod'", payload));
        assertTrue(evaluator.evaluate("tags contains 'staging' || userIdentity.arn contains 'admin'", payload));
        assertFalse(evaluator.evaluate("tags contains 'staging' || userIdentity.arn contains 'readonly'", payload));
    }
}
