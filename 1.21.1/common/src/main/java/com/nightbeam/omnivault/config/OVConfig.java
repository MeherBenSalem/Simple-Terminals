package com.nightbeam.omnivault.config;

import com.nightbeam.omnivault.OmniVault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * A small TOML-compatible config shared by every loader. Features declare values as static fields
 * in their own config classes; {@link #load(Path)} reads {@code config/omnivault-common.toml}
 * and rewrites it so new keys and comments always appear.
 */
public final class OVConfig {
    private static final List<Value<?>> VALUES = new ArrayList<>();
    private static final String FILE_NAME = "omnivault-common.toml";

    private OVConfig() {
    }

    public static Value<Boolean> bool(String section, String key, boolean def, String comment) {
        return add(new Value<>(section, key, def, comment, s -> switch (s.toLowerCase(Locale.ROOT)) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> null;
        }, String::valueOf));
    }

    public static Value<Integer> integer(String section, String key, int def, int min, int max, String comment) {
        return add(new Value<>(section, key, def, comment + " [" + min + " .. " + max + "]", s -> {
            try {
                int v = Integer.parseInt(s.replace("_", ""));
                return Math.max(min, Math.min(max, v));
            } catch (NumberFormatException e) {
                return null;
            }
        }, String::valueOf));
    }

    public static Value<Long> longValue(String section, String key, long def, long min, long max, String comment) {
        return add(new Value<>(section, key, def, comment + " [" + min + " .. " + max + "]", s -> {
            try {
                long v = Long.parseLong(s.replace("_", ""));
                return Math.max(min, Math.min(max, v));
            } catch (NumberFormatException e) {
                return null;
            }
        }, String::valueOf));
    }

    public static Value<Double> decimal(String section, String key, double def, double min, double max, String comment) {
        return add(new Value<>(section, key, def, comment + " [" + min + " .. " + max + "]", s -> {
            try {
                double v = Double.parseDouble(s);
                return Math.max(min, Math.min(max, v));
            } catch (NumberFormatException e) {
                return null;
            }
        }, String::valueOf));
    }

    private static synchronized <T> Value<T> add(Value<T> value) {
        VALUES.add(value);
        return value;
    }

    public static synchronized void load(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        Map<String, String> raw = new HashMap<>();
        if (Files.exists(file)) {
            try {
                String section = "";
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                        section = trimmed.substring(1, trimmed.length() - 1).strip();
                        continue;
                    }
                    int eq = trimmed.indexOf('=');
                    if (eq > 0) {
                        String value = trimmed.substring(eq + 1).strip();
                        int comment = value.indexOf(" #");
                        if (comment >= 0) {
                            value = value.substring(0, comment).strip();
                        }
                        raw.put(section + "." + trimmed.substring(0, eq).strip(), unquote(value));
                    }
                }
            } catch (IOException e) {
                OmniVault.LOG.error("Could not read {}, using defaults", file, e);
            }
        }
        for (Value<?> value : VALUES) {
            String text = raw.get(value.section + "." + value.key);
            value.reset();
            if (text != null && !value.parse(text)) {
                OmniVault.LOG.warn("Invalid value '{}' for {}.{} in {}, using {}", text, value.section, value.key, FILE_NAME, value.get());
            }
        }
        write(file);
    }

    private static String unquote(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"") ? value.substring(1, value.length() - 1) : value;
    }

    private static void write(Path file) {
        StringBuilder out = new StringBuilder("# OmniVault common configuration. Values are clamped to the range shown.\n");
        String section = null;
        List<Value<?>> sorted = new ArrayList<>(VALUES);
        sorted.sort((a, b) -> a.section.compareTo(b.section));
        for (Value<?> value : sorted) {
            if (!value.section.equals(section)) {
                section = value.section;
                out.append('\n').append('[').append(section).append("]\n");
            }
            out.append("# ").append(value.comment).append(" (default: ").append(value.formatDefault()).append(")\n");
            out.append(value.key).append(" = ").append(value.formatCurrent()).append('\n');
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            OmniVault.LOG.error("Could not write {}", file, e);
        }
    }

    public static final class Value<T> {
        private final String section;
        private final String key;
        private final T def;
        private final String comment;
        private final Function<String, T> parser;
        private final Function<T, String> formatter;
        private volatile T current;

        private Value(String section, String key, T def, String comment, Function<String, T> parser, Function<T, String> formatter) {
            this.section = section;
            this.key = key;
            this.def = def;
            this.comment = comment;
            this.parser = parser;
            this.formatter = formatter;
            this.current = def;
        }

        public T get() {
            return current;
        }

        private void reset() {
            current = def;
        }

        private boolean parse(String text) {
            T parsed = parser.apply(text);
            if (parsed == null) {
                return false;
            }
            current = parsed;
            return true;
        }

        private String formatDefault() {
            return formatter.apply(def);
        }

        private String formatCurrent() {
            return formatter.apply(current);
        }
    }
}
