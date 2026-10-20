package com.example.aichecker;

public record DisclosureResult(boolean disclosed, String evidence, String classification, String source) {
    public DisclosureResult(boolean disclosed, String evidence) {
        this(disclosed, evidence, disclosed ? "possible_ambiguous" : "none", "unknown");
    }
}
