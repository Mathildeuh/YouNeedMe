package fr.mathildeuh.youneedme.modules.scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Parses one title/line entry from {@code modules/scoreboard.yml} and picks the frame that should
 * currently be showing. An entry is one of:
 *
 * <ul>
 *   <li>a plain string - static, never changes;
 *   <li>a YAML list of strings - cycled through in order (loop mode, the shared {@code
 *       animation-interval-ticks} speed);
 *   <li>a YAML map with either {@code frames: [...]} or {@code scroll: "...", style: "...", width:
 *       N} (auto-generates a scrolling marquee), plus optional {@code interval-ticks} and {@code
 *       mode: loop|bounce|once} overrides.
 * </ul>
 */
final class ScoreboardAnimation {

    enum Mode {
        LOOP,
        BOUNCE,
        ONCE
    }

    private final List<String> frames;
    private final long intervalTicks;
    private final Mode mode;

    private ScoreboardAnimation(List<String> frames, long intervalTicks, Mode mode) {
        this.frames = frames;
        this.intervalTicks = intervalTicks;
        this.mode = mode;
    }

    static ScoreboardAnimation parse(@Nullable Object raw, long defaultIntervalTicks) {
        if (raw instanceof Map<?, ?> map) {
            return parseMap(map, defaultIntervalTicks);
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return new ScoreboardAnimation(stringify(list), defaultIntervalTicks, Mode.LOOP);
        }
        return new ScoreboardAnimation(
                List.of(raw == null ? "" : raw.toString()), defaultIntervalTicks, Mode.LOOP);
    }

    private static ScoreboardAnimation parseMap(Map<?, ?> map, long defaultIntervalTicks) {
        long intervalTicks = defaultIntervalTicks;
        if (map.get("interval-ticks") instanceof Number number) {
            intervalTicks = Math.max(1, number.longValue());
        }
        Mode mode = parseMode(map.get("mode"));

        Object scrollRaw = map.get("scroll");
        if (scrollRaw != null) {
            String style = map.get("style") == null ? "" : map.get("style").toString();
            int width = map.get("width") instanceof Number number ? number.intValue() : 20;
            return new ScoreboardAnimation(
                    scrollFrames(scrollRaw.toString(), style, Math.max(1, width)),
                    intervalTicks,
                    mode);
        }
        if (map.get("frames") instanceof List<?> list && !list.isEmpty()) {
            return new ScoreboardAnimation(stringify(list), intervalTicks, mode);
        }
        return new ScoreboardAnimation(List.of(""), intervalTicks, mode);
    }

    private static Mode parseMode(@Nullable Object raw) {
        if (raw == null) {
            return Mode.LOOP;
        }
        return switch (raw.toString().toLowerCase(Locale.ROOT)) {
            case "bounce", "pingpong", "ping-pong" -> Mode.BOUNCE;
            case "once" -> Mode.ONCE;
            default -> Mode.LOOP;
        };
    }

    private static List<String> stringify(List<?> raw) {
        List<String> frames = new ArrayList<>(raw.size());
        for (Object element : raw) {
            frames.add(element == null ? "" : element.toString());
        }
        return frames.isEmpty() ? List.of("") : frames;
    }

    /**
     * Generates a scrolling window of {@code width} characters over {@code text}, wrapping around
     * through a gap so the loop reads continuously. {@code style} is a constant MiniMessage prefix
     * applied to every frame rather than part of the scrolled text itself, so a tag is never cut
     * mid-window.
     */
    private static List<String> scrollFrames(String text, String style, int width) {
        if (text.isEmpty() || text.length() <= width) {
            return List.of(style + text);
        }
        String gap = " ".repeat(Math.max(3, width / 3));
        String doubled = text + gap + text;
        int frameCount = text.length() + gap.length();
        List<String> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            frames.add(style + doubled.substring(i, i + width));
        }
        return frames;
    }

    /**
     * @param renderTick monotonically increasing render-loop counter (see ScoreboardRenderer)
     * @param refreshIntervalTicks how often the render loop itself runs, in ticks - animation can
     *     never visibly advance faster than this, however low {@code intervalTicks} is set
     */
    String frameAt(long renderTick, long refreshIntervalTicks) {
        long framesPerAdvance = Math.max(1, intervalTicks / Math.max(1, refreshIntervalTicks));
        long counter = renderTick / framesPerAdvance;
        int frameCount = frames.size();
        int index =
                switch (mode) {
                    case LOOP -> (int) (counter % frameCount);
                    case ONCE -> (int) Math.min(counter, frameCount - 1);
                    case BOUNCE -> bounceIndex(counter, frameCount);
                };
        return frames.get(index);
    }

    private static int bounceIndex(long counter, int frameCount) {
        if (frameCount <= 1) {
            return 0;
        }
        int period = 2 * (frameCount - 1);
        int pos = (int) (counter % period);
        return pos < frameCount ? pos : period - pos;
    }
}
