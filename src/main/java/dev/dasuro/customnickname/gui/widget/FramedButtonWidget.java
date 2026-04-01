package dev.dasuro.customnickname.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Draw helper for top navigation tabs.
 */
public final class FramedButtonWidget {
    private static final int TAB_ACTIVE_BORDER = 0xFFA0A0A0;
    private static final int TAB_ACTIVE_BG = 0x00101010;
    private static final int TAB_ACTIVE_TEXT = 0xFFF2F2F2;

    private static final int TAB_INACTIVE_BORDER = 0xFF6D6D6D;
    private static final int TAB_INACTIVE_BG = 0x66000000;
    private static final int TAB_INACTIVE_BG_HOVER = 0x88000000;
    private static final int TAB_INACTIVE_TEXT = 0xFFDCDCDC;

    private static final int ACTIVE_INDICATOR_INSET = 14;
    private static final int ACTIVE_INDICATOR_COLOR = 0xFFE4E4E4;

    private FramedButtonWidget() {
    }

    public static void renderTab(GuiGraphicsExtractor context, Button button, Component label, int mouseX, int mouseY, boolean activeTab) {
        int x = button.getX();
        int y = button.getY();
        int w = button.getWidth();
        int h = button.getHeight();

        boolean hovered = button.isMouseOver(mouseX, mouseY);
        int border = activeTab ? TAB_ACTIVE_BORDER : TAB_INACTIVE_BORDER;
        int bg = activeTab ? TAB_ACTIVE_BG : (hovered ? TAB_INACTIVE_BG_HOVER : TAB_INACTIVE_BG);
        int textColor = activeTab ? TAB_ACTIVE_TEXT : TAB_INACTIVE_TEXT;

        // Fill tab body
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);

        // Border: active tab is intentionally open at the bottom.
        context.fill(x, y, x + w, y + 1, border);
        context.fill(x, y, x + 1, y + h, border);
        context.fill(x + w - 1, y, x + w, y + h, border);
        if (!activeTab) {
            context.fill(x, y + h - 1, x + w, y + h, border);
        } else {
            // Keep the active tab open at the bottom, but still show a short center indicator.
            int indicatorLeft = x + ACTIVE_INDICATOR_INSET;
            int indicatorRight = x + w - ACTIVE_INDICATOR_INSET;
            if (indicatorRight > indicatorLeft) {
                context.fill(indicatorLeft, y + h - 1, indicatorRight, y + h, ACTIVE_INDICATOR_COLOR);
            }
        }

        if (button.isFocused() && !activeTab) {
            context.fill(x + 1, y + 1, x + w - 1, y + 2, 0x44FFFFFF);
        }

        var textRenderer = Minecraft.getInstance().font;

        context.centeredText(
                textRenderer,
                label,
                x + w / 2,
                y + (h - 8) / 2,
                textColor
        );
    }
}

