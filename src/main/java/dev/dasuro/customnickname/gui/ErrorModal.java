package dev.dasuro.customnickname.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ErrorModal {
    private static final int MAX_WIDTH = 340;
    private static final int SIDE_MARGIN = 20;
    private static final int BOX_PADDING = 10;
    private static final int TITLE_GAP = 8;
    private static final int FOOTER_GAP = 12;

    private final TextRenderer textRenderer;
    private final ButtonWidget closeButton;

    private boolean open;
    private Text message = Text.empty();
    private List<String> wrappedLines = List.of();

    private int screenWidth;
    private int screenHeight;

    private int boxX;
    private int boxY;
    private int boxWidth;
    private int boxHeight;

    public ErrorModal(TextRenderer textRenderer) {
        this.textRenderer = textRenderer;
        this.closeButton = ButtonWidget.builder(Text.translatable("gui.customnickname.close"), b -> close())
                .dimensions(0, 0, 80, 20)
                .build();
    }

    public void setScreenSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
        layout();
    }

    public void show(Text message) {
        this.message = message == null ? Text.empty() : message;
        this.open = true;
        this.wrappedLines = wrapText(this.message.getString(), Math.max(120, getModalWidth() - BOX_PADDING * 2));
        layout();
    }

    public void close() {
        this.open = false;
    }

    public boolean isOpen() {
        return this.open;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!open) return;

        context.fill(0, 0, screenWidth, screenHeight, 0xAA000000);

        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF5A5A5A);
        context.fill(boxX + 1, boxY + 1, boxX + boxWidth - 1, boxY + boxHeight - 1, 0xFF1A1A1A);

        int titleY = boxY + BOX_PADDING;
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("gui.customnickname.error_title").formatted(Formatting.RED),
                boxX + boxWidth / 2,
                titleY,
                0xFFFFFFFF
        );

        int lineY = titleY + textRenderer.fontHeight + TITLE_GAP;
        for (String line : wrappedLines) {
            context.drawCenteredTextWithShadow(textRenderer, line, boxX + boxWidth / 2, lineY, 0xFFFFFFFF);
            lineY += textRenderer.fontHeight + 2;
        }

        closeButton.render(context, mouseX, mouseY, delta);
    }

    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (!open) return false;
        if (isInside(click.x(), click.y())) {
            closeButton.mouseClicked(click, doubleClick);
        }
        return true;
    }

    public boolean mouseReleased(Click click) {
        if (!open) return false;
        closeButton.mouseReleased(click);
        return true;
    }

    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!open) return false;
        closeButton.mouseDragged(click, deltaX, deltaY);
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!open) return false;
        return true;
    }

    public boolean keyPressed(KeyInput keyInput) {
        if (!open) return false;
        int keyCode = keyInput.key();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            close();
            return true;
        }
        return true;
    }

    public boolean charTyped(CharInput charInput) {
        return open;
    }

    private boolean isInside(double mouseX, double mouseY) {
        return mouseX >= boxX
                && mouseX < boxX + boxWidth
                && mouseY >= boxY
                && mouseY < boxY + boxHeight;
    }

    private int getModalWidth() {
        int maxWidth = Math.max(220, screenWidth - SIDE_MARGIN * 2);
        return Math.min(MAX_WIDTH, maxWidth);
    }

    private void layout() {
        this.boxWidth = getModalWidth();
        this.boxHeight = BOX_PADDING
                + textRenderer.fontHeight
                + TITLE_GAP
                + Math.max(1, wrappedLines.size()) * (textRenderer.fontHeight + 2)
                + FOOTER_GAP
                + closeButton.getHeight()
                + BOX_PADDING;

        this.boxX = (screenWidth - boxWidth) / 2;
        this.boxY = (screenHeight - boxHeight) / 2;

        int closeX = boxX + (boxWidth - closeButton.getWidth()) / 2;
        int closeY = boxY + boxHeight - BOX_PADDING - closeButton.getHeight();
        closeButton.setPosition(closeX, closeY);
    }

    private List<String> wrapText(String text, int maxLineWidth) {
        List<String> lines = new ArrayList<>();

        String normalized = text == null ? "" : text.replace("\r", "");
        String[] rawLines = normalized.split("\\n", -1);
        for (String rawLine : rawLines) {
            appendWrappedLine(lines, rawLine, maxLineWidth);
        }

        if (lines.isEmpty()) {
            lines.add("");
        }

        return lines;
    }

    private void appendWrappedLine(List<String> lines, String text, int maxLineWidth) {
        if (text == null || text.isEmpty()) {
            lines.add("");
            return;
        }

        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            String candidate = current.isEmpty() ? word : current + " " + word;
            if (textRenderer.getWidth(candidate) <= maxLineWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }

            if (!current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }

            if (textRenderer.getWidth(word) <= maxLineWidth) {
                current.append(word);
            } else {
                splitLongWord(lines, word, maxLineWidth);
            }
        }

        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
    }

    private void splitLongWord(List<String> lines, String word, int maxLineWidth) {
        StringBuilder part = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            part.append(word.charAt(i));
            if (textRenderer.getWidth(part.toString()) > maxLineWidth) {
                part.setLength(part.length() - 1);
                if (!part.isEmpty()) {
                    lines.add(part.toString());
                }
                part.setLength(0);
                part.append(word.charAt(i));
            }
        }
        if (!part.isEmpty()) {
            lines.add(part.toString());
        }
    }
}


