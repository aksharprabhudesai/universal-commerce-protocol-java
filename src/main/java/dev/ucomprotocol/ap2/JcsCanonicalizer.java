package dev.ucomprotocol.ap2;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * RFC 8785 JSON Canonicalization Scheme (JCS) serializer used to produce
 * deterministic UTF-8 byte representations of UCP checkout payloads prior to
 * AP2 JWS signing and verification.
 */
public final class JcsCanonicalizer {

    private JcsCanonicalizer() {
    }

    /**
     * Canonicalizes a Jackson {@link JsonNode} according to RFC 8785.
     *
     * @param node the JSON tree to canonicalize
     * @return canonical UTF-8 bytes
     */
    public static byte[] canonicalize(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        writeNode(node, sb);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Canonicalizes a JSON object while excluding a top-level field (for example,
     * excluding {@code "ap2"} when computing {@code ap2.merchant_authorization}).
     *
     * @param node          the JSON object node
     * @param excludedField top-level field name to omit
     * @return canonical UTF-8 bytes
     */
    public static byte[] canonicalizeWithoutField(JsonNode node, String excludedField) {
        if (node != null && node.isObject()) {
            ObjectNode copy = ((ObjectNode) node).deepCopy();
            copy.remove(excludedField);
            return canonicalize(copy);
        }
        return canonicalize(node);
    }

    private static void writeNode(JsonNode node, StringBuilder sb) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            sb.append("null");
        } else if (node.isBoolean()) {
            sb.append(node.booleanValue() ? "true" : "false");
        } else if (node.isNumber()) {
            writeNumber(node.decimalValue(), sb);
        } else if (node.isString()) {
            writeString(node.asString(), sb);
        } else if (node.isArray()) {
            sb.append('[');
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                writeNode(node.get(i), sb);
            }
            sb.append(']');
        } else if (node.isObject()) {
            sb.append('{');
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                names.add(entry.getKey());
            }
            // Java String.compareTo sorts by UTF-16 code units per RFC 8785 Section 3.2.3
            Collections.sort(names);
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                String key = names.get(i);
                writeString(key, sb);
                sb.append(':');
                writeNode(node.get(key), sb);
            }
            sb.append('}');
        } else {
            writeString(node.asString(), sb);
        }
    }

    private static void writeNumber(BigDecimal value, StringBuilder sb) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.compareTo(BigDecimal.ZERO) == 0) {
            sb.append('0');
            return;
        }
        int exponent = normalized.precision() - normalized.scale() - 1;
        if (exponent >= -6 && exponent < 21) {
            sb.append(normalized.toPlainString());
        } else {
            String sci = normalized.toString().replace("E", "e");
            if (sci.contains("e") && !sci.contains("e-") && !sci.contains("e+")) {
                sci = sci.replace("e", "e+");
            }
            sb.append(sci);
        }
    }

    private static void writeString(String value, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
