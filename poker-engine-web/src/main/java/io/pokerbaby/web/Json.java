package io.pokerbaby.web;

import java.util.Map;
import java.util.stream.Collectors;

// Output-only encoding for this demo's small, explicitly projected API.
// Hand-history serialization remains the responsibility of poker-engine-json.
final class Json {
    private Json() {
    }

    static String encode(Object value) {
        return switch (value) {
            case null -> "null";
            case String string -> quote(string);
            case Number number -> number.toString();
            case Boolean bool -> bool.toString();
            case Map<?, ?> map -> map.entrySet().stream()
                    .map(entry -> quote(entry.getKey().toString()) + ":" + encode(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
            case Iterable<?> items -> {
                StringBuilder out = new StringBuilder("[");
                for (Object item : items) {
                    if (out.length() > 1) {
                        out.append(',');
                    }
                    out.append(encode(item));
                }
                yield out.append(']').toString();
            }
            default -> throw new IllegalArgumentException("Unsupported JSON value");
        };
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
