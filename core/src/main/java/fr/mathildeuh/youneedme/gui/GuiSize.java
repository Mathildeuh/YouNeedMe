package fr.mathildeuh.youneedme.gui;

/** Rounds an item count up to a chest inventory size (a multiple of 9, clamped to [9, 54]). */
public final class GuiSize {

    private GuiSize() {}

    public static int fit(int itemCount) {
        int size = ((Math.max(itemCount, 1) + 8) / 9) * 9;
        return Math.min(Math.max(size, 9), 54);
    }
}
