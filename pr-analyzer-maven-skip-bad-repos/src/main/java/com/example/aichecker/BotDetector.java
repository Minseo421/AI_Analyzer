package com.example.aichecker;

import java.util.Locale;

public class BotDetector {
    public static final String REVISION = "api-type-suffix-review-v2";
    public enum Assessment { HUMAN, BOT, REVIEW }
    public static Assessment assess(String login, String userType) {
        String l = login == null ? "" : login.toLowerCase(Locale.ROOT);
        String t = userType == null ? "" : userType.toLowerCase(Locale.ROOT);
        if (t.equals("bot") || l.endsWith("[bot]")) return Assessment.BOT;
        if (l.isBlank() || !t.equals("user")) return Assessment.REVIEW;
        if (l.contains("bot") || l.contains("renovate") || l.contains("github-action")
                || l.contains("pre-commit-ci") || l.contains("mergify") || l.contains("codecov") || l.equals("app")) return Assessment.REVIEW;
        return Assessment.HUMAN;
    }
    public static boolean isHuman(String login, String userType) {
        // Uncertain logins are not silently called bots. StudyRun gates them on a reviewed decision.
        return assess(login, userType) != Assessment.BOT;
    }
}
