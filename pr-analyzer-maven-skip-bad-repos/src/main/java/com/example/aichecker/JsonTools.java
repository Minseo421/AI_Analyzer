package com.example.aichecker;

import java.util.ArrayList;
import java.util.List;

public class JsonTools {
    public static String stringValue(String json, String key, String defaultValue) {
        String value = nullableStringValue(json, key);
        return value == null ? defaultValue : value;
    }

    public static String nullableStringValue(String json, String key) {
        if (json == null) return null;
        int keyIndex = findKey(json, key);
        if (keyIndex < 0) return null;
        int colon = json.indexOf(':', keyIndex);
        if (colon < 0) return null;
        int i = skipSpaces(json, colon + 1);
        if (json.startsWith("null", i)) return null;
        if (i >= json.length() || json.charAt(i) != '"') return null;
        return readJsonString(json, i).value();
    }

    public static int intValue(String json, String key, int defaultValue) {
        if (json == null) return defaultValue;
        int keyIndex = findKey(json, key);
        if (keyIndex < 0) return defaultValue;
        int colon = json.indexOf(':', keyIndex);
        if (colon < 0) return defaultValue;
        int i = skipSpaces(json, colon + 1);
        int start = i;
        while (i < json.length() && (Character.isDigit(json.charAt(i)) || json.charAt(i) == '-')) i++;
        try {
            return Integer.parseInt(json.substring(start, i));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static String objectValue(String json, String key) {
        if (json == null) return "";
        int keyIndex = findKey(json, key);
        if (keyIndex < 0) return "";
        int colon = json.indexOf(':', keyIndex);
        if (colon < 0) return "";
        int i = skipSpaces(json, colon + 1);
        if (i >= json.length() || json.charAt(i) != '{') return "";
        int end = findMatching(json, i, '{', '}');
        return end < 0 ? "" : json.substring(i, end + 1);
    }

    public static List<String> splitTopLevelObjects(String jsonArray) {
        List<String> result = new ArrayList<>();
        if (jsonArray == null) return result;
        int i = 0;
        while (i < jsonArray.length()) {
            char ch = jsonArray.charAt(i);
            if (ch == '{') {
                int end = findMatching(jsonArray, i, '{', '}');
                if (end < 0) break;
                result.add(jsonArray.substring(i, end + 1));
                i = end + 1;
            } else {
                i++;
            }
        }
        return result;
    }

    private static int findKey(String json, String key) {
        String target = "\"" + key + "\"";
        return json.indexOf(target);
    }

    private static int skipSpaces(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private static int findMatching(String s, int start, char open, char close) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (ch == '\\') {
                    escape = true;
                } else if (ch == '"') {
                    inString = false;
                }
            } else {
                if (ch == '"') inString = true;
                else if (ch == open) depth++;
                else if (ch == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private static StringRead readJsonString(String s, int startQuote) {
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (int i = startQuote + 1; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (escape) {
                switch (ch) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 < s.length()) {
                            String hex = s.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append("\\u").append(hex);
                                i += 4;
                            }
                        }
                    }
                    default -> sb.append(ch);
                }
                escape = false;
            } else if (ch == '\\') {
                escape = true;
            } else if (ch == '"') {
                return new StringRead(sb.toString(), i);
            } else {
                sb.append(ch);
            }
        }
        return new StringRead(sb.toString(), s.length());
    }

    private record StringRead(String value, int endIndex) {
    }
}
