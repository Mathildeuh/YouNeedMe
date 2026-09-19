package fr.mathildeuh.youneedme.modules.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScoreboardAnimationTest {

    @Test
    void aPlainStringNeverChanges() {
        ScoreboardAnimation animation = ScoreboardAnimation.parse("<gold>Hello", 20);
        assertEquals("<gold>Hello", animation.frameAt(0, 20));
        assertEquals("<gold>Hello", animation.frameAt(999, 20));
    }

    @Test
    void aPlainListLoopsInOrderAtTheGivenSpeed() {
        ScoreboardAnimation animation = ScoreboardAnimation.parse(List.of("a", "b", "c"), 20);
        // refreshIntervalTicks == intervalTicks == 20, so framesPerAdvance == 1: one frame per tick
        assertEquals("a", animation.frameAt(0, 20));
        assertEquals("b", animation.frameAt(1, 20));
        assertEquals("c", animation.frameAt(2, 20));
        assertEquals("a", animation.frameAt(3, 20));
    }

    @Test
    void perElementIntervalTicksOverridesTheDefaultSpeed() {
        Map<String, Object> map =
                Map.of("frames", List.of("a", "b"), "interval-ticks", 40L, "mode", "loop");
        ScoreboardAnimation animation = ScoreboardAnimation.parse(map, 20);
        // refreshIntervalTicks=20, intervalTicks=40 -> framesPerAdvance=2: one frame every 2
        // renders
        assertEquals("a", animation.frameAt(0, 20));
        assertEquals("a", animation.frameAt(1, 20));
        assertEquals("b", animation.frameAt(2, 20));
        assertEquals("b", animation.frameAt(3, 20));
        assertEquals("a", animation.frameAt(4, 20));
    }

    @Test
    void bounceModePingPongsBetweenTheFirstAndLastFrame() {
        Map<String, Object> map = Map.of("frames", List.of("a", "b", "c"), "mode", "bounce");
        ScoreboardAnimation animation = ScoreboardAnimation.parse(map, 20);
        assertEquals("a", animation.frameAt(0, 20));
        assertEquals("b", animation.frameAt(1, 20));
        assertEquals("c", animation.frameAt(2, 20));
        assertEquals("b", animation.frameAt(3, 20));
        assertEquals("a", animation.frameAt(4, 20));
        assertEquals("b", animation.frameAt(5, 20));
    }

    @Test
    void onceModeFreezesOnTheLastFrame() {
        Map<String, Object> map = Map.of("frames", List.of("a", "b", "c"), "mode", "once");
        ScoreboardAnimation animation = ScoreboardAnimation.parse(map, 20);
        assertEquals("a", animation.frameAt(0, 20));
        assertEquals("b", animation.frameAt(1, 20));
        assertEquals("c", animation.frameAt(2, 20));
        assertEquals("c", animation.frameAt(3, 20));
        assertEquals("c", animation.frameAt(1000, 20));
    }

    @Test
    void scrollGeneratesAFixedWidthWindowOverTheText() {
        Map<String, Object> map = Map.of("scroll", "Hello World", "style", "<yellow>", "width", 5L);
        ScoreboardAnimation animation = ScoreboardAnimation.parse(map, 20);
        String first = animation.frameAt(0, 20);
        assertTrue(first.startsWith("<yellow>"), "every frame keeps the constant style prefix");
        assertEquals("<yellow>Hello".length(), first.length());
    }

    @Test
    void scrollSkipsGeneratingFramesWhenTheTextAlreadyFitsTheWidth() {
        Map<String, Object> map = Map.of("scroll", "Hi", "width", 20L);
        ScoreboardAnimation animation = ScoreboardAnimation.parse(map, 20);
        assertEquals("Hi", animation.frameAt(0, 20));
        assertEquals("Hi", animation.frameAt(500, 20));
    }
}
