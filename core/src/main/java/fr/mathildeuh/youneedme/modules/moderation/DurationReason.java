package fr.mathildeuh.youneedme.modules.moderation;

import fr.mathildeuh.youneedme.util.TimeParser;
import org.jetbrains.annotations.Nullable;

/** Parses the shared {@code <player> [duration] [reason...]} tail used by ban/mute. */
record DurationReason(@Nullable Long durationMillis, String reason) {

    static DurationReason parse(String[] args, String defaultReason) {
        if (args.length == 0) {
            return new DurationReason(null, defaultReason);
        }
        Long duration =
                TimeParser.isValid(args[0]) && !TimeParser.isPermanent(args[0])
                        ? TimeParser.parseMillis(args[0])
                        : null;
        boolean firstIsDuration = duration != null || TimeParser.isPermanent(args[0]);
        int reasonStart = firstIsDuration ? 1 : 0;
        String reason =
                reasonStart >= args.length
                        ? defaultReason
                        : String.join(
                                " ",
                                java.util.Arrays.asList(args).subList(reasonStart, args.length));
        return new DurationReason(duration, reason);
    }
}
