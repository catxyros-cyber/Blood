package dev.bloom.client.core.json;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, strict JSON reader/writer with hard limits, used for BLOOD's own
 * config and for imported profiles/skyboxes. Values map to: {@link Map}
 * (insertion-ordered), {@link List}, {@link String}, {@link Double},
 * {@link Boolean} and {@code null}. Parsing untrusted input never executes
 * anything; it only builds these plain values.
 */
public final class Json {
    private Json() {}

    public static final int MAX_DEPTH = 64;

    public static final class ParseException extends Exception {
        public final int line;
        public final int column;

        ParseException(String message, int line, int column) {
            super(message + " (line " + line + ", column " + column + ")");
            this.line = line;
            this.column = column;
        }
    }

    public static Object parse(String text) throws ParseException {
        return parse(text, 4 * 1024 * 1024);
    }

    public static Object parse(String text, int maxChars) throws ParseException {
        if (text == null) throw new ParseException("No input", 1, 1);
        if (text.length() > maxChars) throw new ParseException("Input is too large", 1, 1);
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value(0);
        p.skipWs();
        if (p.pos < text.length()) throw p.error("Unexpected trailing characters");
        return v;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            // Tolerate a UTF-8 byte order mark.
            this.s = s.startsWith("﻿") ? s.substring(1) : s;
        }

        ParseException error(String msg) {
            int line = 1, col = 1;
            for (int i = 0; i < Math.min(pos, s.length()); i++) {
                if (s.charAt(i) == '\n') {
                    line++;
                    col = 1;
                } else col++;
            }
            return new ParseException(msg, line, col);
        }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else break;
            }
        }

        Object value(int depth) throws ParseException {
            if (depth > MAX_DEPTH) throw error("Nesting is too deep");
            if (pos >= s.length()) throw error("Unexpected end of input");
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> object(depth);
                case '[' -> array(depth);
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) yield number();
                    throw error("Unexpected character '" + c + "'");
                }
            };
        }

        Object literal(String word, Object v) throws ParseException {
            if (!s.startsWith(word, pos)) throw error("Invalid literal");
            pos += word.length();
            return v;
        }

        Map<String, Object> object(int depth) throws ParseException {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++;
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return m;
            }
            while (true) {
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != '"') throw error("Expected a property name");
                String key = string();
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != ':') throw error("Expected ':'");
                pos++;
                skipWs();
                m.put(key, value(depth + 1));
                skipWs();
                if (pos >= s.length()) throw error("Unterminated object");
                char c = s.charAt(pos++);
                if (c == '}') return m;
                if (c != ',') throw error("Expected ',' or '}'");
            }
        }

        List<Object> array(int depth) throws ParseException {
            List<Object> l = new ArrayList<>();
            pos++;
            skipWs();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return l;
            }
            while (true) {
                skipWs();
                l.add(value(depth + 1));
                skipWs();
                if (pos >= s.length()) throw error("Unterminated array");
                char c = s.charAt(pos++);
                if (c == ']') return l;
                if (c != ',') throw error("Expected ',' or ']'");
            }
        }

        String string() throws ParseException {
            pos++; // opening quote
            StringBuilder b = new StringBuilder();
            while (true) {
                if (pos >= s.length()) throw error("Unterminated string");
                char c = s.charAt(pos++);
                if (c == '"') return b.toString();
                if (c == '\\') {
                    if (pos >= s.length()) throw error("Unterminated escape");
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"' -> b.append('"');
                        case '\\' -> b.append('\\');
                        case '/' -> b.append('/');
                        case 'b' -> b.append('\b');
                        case 'f' -> b.append('\f');
                        case 'n' -> b.append('\n');
                        case 'r' -> b.append('\r');
                        case 't' -> b.append('\t');
                        case 'u' -> {
                            if (pos + 4 > s.length()) throw error("Invalid unicode escape");
                            try {
                                b.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw error("Invalid unicode escape");
                            }
                            pos += 4;
                        }
                        default -> throw error("Invalid escape '\\" + e + "'");
                    }
                } else if (c < 0x20) {
                    throw error("Control character in string");
                } else {
                    b.append(c);
                }
            }
        }

        Double number() throws ParseException {
            int start = pos;
            if (s.charAt(pos) == '-') pos++;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') pos++;
                else break;
            }
            String n = s.substring(start, pos);
            try {
                double d = Double.parseDouble(n);
                if (Double.isNaN(d) || Double.isInfinite(d)) throw error("Invalid number");
                return d;
            } catch (NumberFormatException ex) {
                throw error("Invalid number '" + n + "'");
            }
        }
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    public static String write(Object value) {
        StringBuilder b = new StringBuilder();
        write(b, value, 0, true);
        b.append('\n');
        return b.toString();
    }

    public static String writeCompact(Object value) {
        StringBuilder b = new StringBuilder();
        write(b, value, 0, false);
        return b.toString();
    }

    private static void indent(StringBuilder b, int n) {
        for (int i = 0; i < n; i++) b.append("  ");
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder b, Object v, int depth, boolean pretty) {
        if (v == null) {
            b.append("null");
        } else if (v instanceof String str) {
            quote(b, str);
        } else if (v instanceof Boolean bool) {
            b.append(bool ? "true" : "false");
        } else if (v instanceof Number num) {
            double d = num.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) b.append('0');
            else if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(trimDouble(d));
        } else if (v instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                b.append("{}");
                return;
            }
            b.append('{');
            Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> e = it.next();
                if (pretty) {
                    b.append('\n');
                    indent(b, depth + 1);
                }
                quote(b, String.valueOf(e.getKey()));
                b.append(pretty ? ": " : ":");
                write(b, e.getValue(), depth + 1, pretty);
                if (it.hasNext()) b.append(',');
            }
            if (pretty) {
                b.append('\n');
                indent(b, depth);
            }
            b.append('}');
        } else if (v instanceof Iterable<?> list) {
            Iterator<?> it = list.iterator();
            if (!it.hasNext()) {
                b.append("[]");
                return;
            }
            // Short lists of scalars stay on one line.
            boolean scalars = true;
            int count = 0;
            for (Object o : list) {
                count++;
                if (o instanceof Map || o instanceof Iterable) scalars = false;
            }
            boolean inline = !pretty || (scalars && count <= 8);
            b.append('[');
            while (it.hasNext()) {
                if (!inline) {
                    b.append('\n');
                    indent(b, depth + 1);
                }
                write(b, it.next(), depth + 1, pretty);
                if (it.hasNext()) b.append(inline && pretty ? ", " : ",");
            }
            if (!inline) {
                b.append('\n');
                indent(b, depth);
            }
            b.append(']');
        } else {
            quote(b, v.toString());
        }
    }

    private static String trimDouble(double d) {
        String s = Double.toString(d);
        if (s.contains("E")) return s;
        // Avoid long binary tails like 0.30000000000000004.
        String r = String.format(java.util.Locale.ROOT, "%.6f", d);
        r = r.replaceAll("0+$", "");
        if (r.endsWith(".")) r = r.substring(0, r.length() - 1);
        return r;
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        b.append('"');
    }

    // ------------------------------------------------------------------
    // Typed access helpers (never throw; return the fallback on mismatch)
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object v) {
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object v) {
        return v instanceof List<?> l ? (List<Object>) l : null;
    }

    public static String str(Map<String, Object> m, String key, String fallback) {
        Object v = m == null ? null : m.get(key);
        return v instanceof String s ? s : fallback;
    }

    public static double num(Map<String, Object> m, String key, double fallback) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    public static boolean bool(Map<String, Object> m, String key, boolean fallback) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Boolean b ? b : fallback;
    }
}
