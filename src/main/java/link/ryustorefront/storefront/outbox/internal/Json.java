package link.ryustorefront.storefront.outbox.internal;

import java.util.Map;
import java.util.TreeMap;

/**
 * A minimal writer for flat JSON objects of string values, so event payloads need no JSON library and
 * no hand-built strings elsewhere. Escapes per RFC 8259; Postgres parses the result as jsonb.
 */
public final class Json {

    private Json() {
    }

    /** Keys are written in sorted order so the output is deterministic. A null value becomes JSON null. */
    public static String object(Map<String, String> fields) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : new TreeMap<>(fields).entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            string(out, e.getKey());
            out.append(':');
            if (e.getValue() == null) {
                out.append("null");
            } else {
                string(out, e.getValue());
            }
        }
        return out.append('}').toString();
    }

    private static void string(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
