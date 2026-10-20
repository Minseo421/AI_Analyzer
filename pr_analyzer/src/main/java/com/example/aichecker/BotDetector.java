package com.example.aichecker;

import java.util.Locale;

public class BotDetector {
    public static boolean isHuman(String login, String userType) {
        String l = login == null ? "" : login.toLowerCase(Locale.ROOT);
        String t = userType == null ? "" : userType.toLowerCase(Locale.ROOT);
        if (t.equals("bot")) return false;
        if (l.endsWith("[bot]")) return false;
        return !(l.contains("dependabot")
                || l.contains("renovate")
                || l.contains("github-actions")
                || l.contains("github-action")
                || l.contains("pre-commit-ci")
                || l.contains("mergify")
                || l.contains("snyk-bot")
                || l.contains("codecov")
                || l.equals("app")
                || l.contains("bot"));
    }
}
