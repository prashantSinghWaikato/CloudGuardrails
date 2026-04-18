package com.cloud.guardrails.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ConditionEvaluator {

    private static final Pattern CONDITION_PATTERN = Pattern.compile(
            "^(.+?)\\s*(==|!=|>=|<=|>|<|contains|exists)\\s*(.*)$"
    );

    public boolean evaluate(String condition, JsonNode payload) {

        if (condition == null || condition.isEmpty()) {
            return true;
        }

        try {
            JsonNode safePayload = payload != null ? payload : NullNode.getInstance();

            for (String orClause : split(condition, "||")) {
                if (evaluateAndClause(orClause, safePayload)) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean evaluateAndClause(String clause, JsonNode payload) {
        for (String part : split(clause, "&&")) {
            if (!evaluateExpression(part.trim(), payload)) {
                return false;
            }
        }

        return true;
    }

    private boolean evaluateExpression(String expression, JsonNode payload) {
        Matcher matcher = CONDITION_PATTERN.matcher(expression.trim());
        if (!matcher.matches()) {
            return false;
        }

        String key = matcher.group(1).trim();
        String operator = matcher.group(2).trim();
        String rawExpected = matcher.group(3).trim();

        JsonNode actualNode = resolveNode(payload, key);

        return switch (operator) {
            case "exists" -> actualNode != null && !actualNode.isMissingNode() && !actualNode.isNull();
            case "contains" -> contains(actualNode, parseLiteral(rawExpected));
            case "==" -> compare(actualNode, parseLiteral(rawExpected)) == 0;
            case "!=" -> compare(actualNode, parseLiteral(rawExpected)) != 0;
            case ">" -> compare(actualNode, parseLiteral(rawExpected)) > 0;
            case "<" -> compare(actualNode, parseLiteral(rawExpected)) < 0;
            case ">=" -> compare(actualNode, parseLiteral(rawExpected)) >= 0;
            case "<=" -> compare(actualNode, parseLiteral(rawExpected)) <= 0;
            default -> false;
        };
    }

    private JsonNode resolveNode(JsonNode payload, String key) {
        if (payload == null) {
            return NullNode.getInstance();
        }

        String pointer = "/" + key.trim().replace(".", "/");
        JsonNode node = payload.at(pointer);
        if (!node.isMissingNode()) {
            return node;
        }

        return payload.get(key);
    }

    private int compare(JsonNode actualNode, Object expected) {
        if (actualNode == null || actualNode.isMissingNode() || actualNode.isNull()) {
            return -1;
        }

        if (expected instanceof Boolean expectedBoolean) {
            return Boolean.compare(actualNode.asBoolean(), expectedBoolean);
        }

        if (expected instanceof BigDecimal expectedNumber) {
            try {
                return new BigDecimal(actualNode.asText()).compareTo(expectedNumber);
            } catch (NumberFormatException ex) {
                return actualNode.asText().compareTo(expected.toString());
            }
        }

        String actual = normalize(actualNode.asText());
        String expectedText = normalize(String.valueOf(expected));
        return actual.compareTo(expectedText);
    }

    private boolean contains(JsonNode actualNode, Object expected) {
        if (actualNode == null || actualNode.isMissingNode() || actualNode.isNull()) {
            return false;
        }

        String expectedText = normalize(String.valueOf(expected));

        if (actualNode.isArray()) {
            for (JsonNode item : actualNode) {
                if (normalize(item.asText()).equals(expectedText)) {
                    return true;
                }
            }
            return false;
        }

        return normalize(actualNode.asText()).contains(expectedText);
    }

    private Object parseLiteral(String rawValue) {
        String value = rawValue.trim();

        if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }

        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private List<String> split(String expression, String delimiter) {
        return List.of(expression.split(Pattern.quote(delimiter)));
    }
}
