package fr.mathildeuh.youneedme.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Parses ban/mute-style durations like {@code 1d2h30m}, {@code 45m}, or {@code perm}/{@code
 * permanent}.
 */
public final class TimeParser {

    private static final Pattern SEGMENT = Pattern.compile("(\\d+)([smhdwMy])");
    private static final Pattern PERMANENT =
            Pattern.compile("perm(anent)?|-1", Pattern.CASE_INSENSITIVE);

    private TimeParser() {}

    /**
     * @return {@code null} for a permanent/unparseable duration, otherwise milliseconds from now.
     *     Callers distinguish "permanent" from "invalid" via {@link #isPermanent}/{@link #isValid}.
     */
    public static @Nullable Long parseMillis(String input) {
        if (isPermanent(input)) {
            return null;
        }
        Matcher matcher = SEGMENT.matcher(input);
        long totalMillis = 0;
        boolean matchedAny = false;
        int consumed = 0;
        while (matcher.find()) {
            matchedAny = true;
            consumed += matcher.end() - matcher.start();
            long amount = Long.parseLong(matcher.group(1));
            totalMillis += amount * unitMillis(matcher.group(2));
        }
        if (!matchedAny || consumed != input.length()) {
            return null;
        }
        return totalMillis;
    }

    public static boolean isPermanent(String input) {
        return PERMANENT.matcher(input.trim()).matches();
    }

    public static boolean isValid(String input) {
        return isPermanent(input) || parseMillis(input) != null;
    }

    private static long unitMillis(String unit) {
        return switch (unit) {
            case "s" -> 1000L;
            case "m" -> 60_000L;
            case "h" -> 3_600_000L;
            case "d" -> 86_400_000L;
            case "w" -> 604_800_000L;
            case "M" -> 2_629_800_000L; // ~30.44 days
            case "y" -> 31_557_600_000L; // ~365.25 days
            default -> throw new IllegalArgumentException("Unknown duration unit: " + unit);
        };
    }

    /** Formats a millisecond duration back into a compact human string, e.g. {@code 1d2h30m}. */
    public static String format(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        long seconds = millis / 1000;
        long days = seconds / 86_400;
        seconds %= 86_400;
        long hours = seconds / 3_600;
        seconds %= 3_600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append('d');
        }
        if (hours > 0) {
            builder.append(hours).append('h');
        }
        if (minutes > 0) {
            builder.append(minutes).append('m');
        }
        if (seconds > 0 || builder.isEmpty()) {
            builder.append(seconds).append('s');
        }
        return builder.toString();
    }
}
