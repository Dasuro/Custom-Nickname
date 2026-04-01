package dev.dasuro.customnickname.util;

import net.minecraft.network.chat.Style;

/**
 * Holds a piece of literal text together with its style.
 * Used for flattening Minecraft Text trees.
 */
public class StyledSegment {
    public final String text;
    public final Style style;

    public StyledSegment(String text, Style style) {
        this.text = text;
        this.style = style != null ? style : Style.EMPTY;
    }
}

