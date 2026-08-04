package dev.dasuro.customnickname.util;

import dev.dasuro.customnickname.config.NickEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorParser {

    private static final Pattern CODE_PATTERN = Pattern.compile(
            "&#([0-9A-Fa-f]{6})|&([0-9a-fk-orA-FK-OR])"
    );

    /** Matches only actual color codes: hex &#RRGGBB or legacy &0-9, &a-f (no formatting codes). */
    private static final Pattern ACTUAL_COLOR_PATTERN = Pattern.compile(
            "&#[0-9A-Fa-f]{6}|&[0-9a-fA-F]"
    );

    /** Matches only formatting codes: &l, &o, &n, &m, &k, &r (case-insensitive). */
    private static final Pattern FORMATTING_ONLY_PATTERN = Pattern.compile(
            "&[k-orK-OR]"
    );

    /** Matches a trailing, incomplete hex color introducer that can happen while typing/pasting. */
    private static final Pattern TRAILING_INCOMPLETE_HEX = Pattern.compile(
            "&#[0-9A-Fa-f]{0,5}$"
    );

    /** Number of characters for one full rainbow cycle. */
    private static final double RAINBOW_WAVELENGTH = 20.0;

    /**
     * A single visible character together with its style.
     * For rainbow parsing this style contains only formatting.
     * For extracted server text it contains the full effective style.
     */
    private record StyledChar(String character, Style style) {
    }

    public static MutableComponent buildNick(
            NickEntry nick,
            MutableComponent serverOriginal
    ) {
        if (nick == null) return Component.empty();

        String nickname = nick.nickname;
        if (nickname == null) nickname = "";

        if (nick.rainbow) {
            List<StyledChar> chars = parseToStyledChars(nickname);
            return rainbowWave(chars, System.currentTimeMillis(), nick.rainbowSpeed);
        }

        if (hasActualColorCodes(nickname)) {
            return parse(nickname);
        }

        if (hasFormattingOnlyCodes(nickname)) {
            return parseWithOriginalColor(nickname, serverOriginal);
        }

        return applyOriginalStyle(nickname, serverOriginal);
    }

    public static MutableComponent parse(String input) {
        if (input == null || input.isEmpty()) return Component.empty();

        input = TRAILING_INCOMPLETE_HEX.matcher(input).replaceAll("");

        MutableComponent result = Component.empty();
        Matcher matcher = CODE_PATTERN.matcher(input);
        int lastEnd = 0;
        Style currentStyle = Style.EMPTY;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                result.append(
                        Component.literal(input.substring(lastEnd, matcher.start()))
                                .setStyle(currentStyle)
                );
            }

            if (matcher.group(1) != null) {
                int rgb = Integer.parseInt(matcher.group(1), 16);
                currentStyle = currentStyle.withColor(TextColor.fromRgb(rgb));
            } else {
                char code = Character.toLowerCase(matcher.group(2).charAt(0));
                currentStyle = applyLegacy(currentStyle, code);
            }

            lastEnd = matcher.end();
        }

        if (lastEnd < input.length()) {
            result.append(
                    Component.literal(input.substring(lastEnd))
                            .setStyle(currentStyle)
            );
        }

        return result;
    }

    /**
     * Parses formatting codes from the nickname and returns one StyledChar per
     * visible character. Color codes are ignored because rainbow or server-color
     * fallback provides the color; formatting codes are preserved.
     */
    private static List<StyledChar> parseToStyledChars(String input) {
        List<StyledChar> result = new ArrayList<>();
        if (input == null || input.isEmpty()) return result;

        input = TRAILING_INCOMPLETE_HEX.matcher(input).replaceAll("");

        Matcher matcher = CODE_PATTERN.matcher(input);
        int lastEnd = 0;
        Style formatting = Style.EMPTY;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                appendCodePointChars(
                        input.substring(lastEnd, matcher.start()),
                        formatting,
                        result
                );
            }

            if (matcher.group(1) == null) {
                char code = Character.toLowerCase(matcher.group(2).charAt(0));
                if (code == 'r') {
                    formatting = Style.EMPTY;
                } else if (isFormattingCode(code)) {
                    formatting = applyFormattingOnly(formatting, code);
                }
            }

            lastEnd = matcher.end();
        }

        if (lastEnd < input.length()) {
            appendCodePointChars(input.substring(lastEnd), formatting, result);
        }

        return result;
    }

    /**
     * Builds a rainbow-wave text from pre-parsed styled characters.
     * Each character keeps its formatting but gets its color from the rainbow.
     */
    private static MutableComponent rainbowWave(
            List<StyledChar> chars,
            long timeMs,
            float speed
    ) {
        MutableComponent result = Component.empty();
        if (chars == null || chars.isEmpty()) return result;

        int len = chars.size();
        double offset = -(timeMs / 1000.0) * speed * 0.25;

        for (int i = 0; i < len; i++) {
            double hue = (i / RAINBOW_WAVELENGTH + offset) % 1.0;
            if (hue < 0) hue += 1.0;

            int rgb = java.awt.Color.HSBtoRGB((float) hue, 1.0f, 1.0f)
                    & 0xFFFFFF;

            StyledChar sc = chars.get(i);
            Style style = sc.style().withColor(TextColor.fromRgb(rgb));

            result.append(Component.literal(sc.character()).setStyle(style));
        }

        return result;
    }

    public static MutableComponent applyOriginalStyle(
            String newText,
            MutableComponent original
    ) {
        if (newText == null || newText.isEmpty()) return Component.empty();

        List<String> targetChars = splitVisibleChars(newText);
        if (targetChars.isEmpty()) return Component.empty();

        List<StyledChar> sourceChars = extractStyledChars(original);

        MutableComponent result = Component.empty();
        for (int i = 0; i < targetChars.size(); i++) {
            Style mapped = mapOriginalStyle(sourceChars, i, targetChars.size());
            result.append(
                    Component.literal(targetChars.get(i)).setStyle(mapped)
            );
        }

        return result;
    }

    public static boolean hasColorCodes(String input) {
        if (input == null || input.isEmpty()) return false;
        return CODE_PATTERN.matcher(input).find();
    }

    /** Returns true only if the input contains actual color codes (hex or legacy 0-9, a-f). */
    public static boolean hasActualColorCodes(String input) {
        if (input == null || input.isEmpty()) return false;
        return ACTUAL_COLOR_PATTERN.matcher(input).find();
    }

    /** Returns true if the input contains formatting-only codes (l, o, n, m, k, r) but no actual colors. */
    public static boolean hasFormattingOnlyCodes(String input) {
        if (input == null || input.isEmpty()) return false;
        return FORMATTING_ONLY_PATTERN.matcher(input).find();
    }

    /**
     * Parses formatting codes (&l, &o, ...) from the nickname, but keeps the
     * server color / gradient from the original player name.
     */
    public static MutableComponent parseWithOriginalColor(
            String input,
            MutableComponent serverOriginal
    ) {
        if (input == null || input.isEmpty()) return Component.empty();

        List<StyledChar> nicknameChars = parseToStyledChars(input);
        if (nicknameChars.isEmpty()) return Component.empty();

        List<StyledChar> sourceChars = extractStyledChars(serverOriginal);

        MutableComponent result = Component.empty();
        for (int i = 0; i < nicknameChars.size(); i++) {
            StyledChar nickChar = nicknameChars.get(i);
            Style originalMapped = mapOriginalStyle(
                    sourceChars,
                    i,
                    nicknameChars.size()
            );
            Style merged = overlayFormatting(originalMapped, nickChar.style());

            result.append(
                    Component.literal(nickChar.character()).setStyle(merged)
            );
        }

        return result;
    }

    public static String strip(String input) {
        if (input == null || input.isEmpty()) return "";
        return input.replaceAll(
                "&#[0-9A-Fa-f]{6}|&[0-9a-fk-orA-FK-OR]",
                ""
        );
    }

    private static List<StyledChar> extractStyledChars(Component text) {
        List<StyledChar> result = new ArrayList<>();
        flattenText(text, Style.EMPTY, result);
        return result;
    }

    private static void flattenText(
            Component text,
            Style inheritedStyle,
            List<StyledChar> out
    ) {
        if (text == null) return;

        Style effectiveStyle = resolveStyle(inheritedStyle, text.getStyle());

        if (text.getContents() instanceof PlainTextContents plain) {
            appendFormattedLiteralAsChars(plain.text(), effectiveStyle, out);
        } else if (text.getContents() instanceof TranslatableContents tc) {
            Object[] args = tc.getArgs();
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof Component argText) {
                        flattenText(argText, effectiveStyle, out);
                    } else if (arg instanceof String argStr && !argStr.isEmpty()) {
                        appendFormattedLiteralAsChars(
                                argStr,
                                effectiveStyle,
                                out
                        );
                    }
                }
            }
        } else {
            String fallback = text.plainCopy().getString();
            if (fallback != null && !fallback.isEmpty()) {
                appendFormattedLiteralAsChars(fallback, effectiveStyle, out);
            }
        }

        for (Component sibling : text.getSiblings()) {
            flattenText(sibling, effectiveStyle, out);
        }
    }

    private static void appendFormattedLiteralAsChars(
            String raw,
            Style baseStyle,
            List<StyledChar> out
    ) {
        if (raw == null || raw.isEmpty()) return;

        Style current = baseStyle != null ? baseStyle : Style.EMPTY;
        int i = 0;

        while (i < raw.length()) {
            if (raw.charAt(i) == '§') {
                int hexColor = parseSectionHexColorAt(raw, i);
                if (hexColor >= 0) {
                    current = current.withColor(TextColor.fromRgb(hexColor));
                    i += 14;
                    continue;
                }

                if (i + 1 < raw.length()) {
                    char code = Character.toLowerCase(raw.charAt(i + 1));
                    if (isLegacySectionCode(code)) {
                        current = applyLegacy(current, code);
                        i += 2;
                        continue;
                    }
                }
            }

            int cp = raw.codePointAt(i);
            out.add(
                    new StyledChar(
                            new String(Character.toChars(cp)),
                            current
                    )
            );
            i += Character.charCount(cp);
        }
    }

    private static void appendCodePointChars(
            String text,
            Style style,
            List<StyledChar> out
    ) {
        if (text == null || text.isEmpty()) return;

        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            out.add(
                    new StyledChar(
                            new String(Character.toChars(cp)),
                            style
                    )
            );
            i += Character.charCount(cp);
        }
    }

    private static List<String> splitVisibleChars(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;

        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            result.add(new String(Character.toChars(cp)));
            i += Character.charCount(cp);
        }

        return result;
    }

    private static Style resolveStyle(Style parent, Style child) {
        if (child == null || child.equals(Style.EMPTY)) {
            return parent != null ? parent : Style.EMPTY;
        }
        if (parent == null || parent.equals(Style.EMPTY)) {
            return child;
        }
        return child.applyTo(parent);
    }

    private static Style mapOriginalStyle(
            List<StyledChar> sourceChars,
            int targetIndex,
            int targetLength
    ) {
        if (sourceChars == null || sourceChars.isEmpty()) {
            return Style.EMPTY;
        }

        double sourcePos = computeSourcePosition(
                targetIndex,
                targetLength,
                sourceChars.size()
        );

        int nearestIndex = clamp(
                (int) Math.round(sourcePos),
                0,
                sourceChars.size() - 1
        );

        Style base = sourceChars.get(nearestIndex).style();
        if (base == null) {
            base = Style.EMPTY;
        }

        Integer interpolatedColor = interpolateColor(sourceChars, sourcePos);
        if (interpolatedColor != null) {
            base = base.withColor(TextColor.fromRgb(interpolatedColor));
        }

        return base;
    }

    private static double computeSourcePosition(
            int targetIndex,
            int targetLength,
            int sourceLength
    ) {
        if (sourceLength <= 1) return 0.0;
        if (targetLength <= 1) return (sourceLength - 1) / 2.0;

        return (double) targetIndex * (sourceLength - 1)
                / (double) (targetLength - 1);
    }

    private static Integer interpolateColor(
            List<StyledChar> sourceChars,
            double sourcePos
    ) {
        if (sourceChars == null || sourceChars.isEmpty()) return null;

        int leftIndex = clamp((int) Math.floor(sourcePos), 0, sourceChars.size() - 1);
        int rightIndex = clamp((int) Math.ceil(sourcePos), 0, sourceChars.size() - 1);

        Integer leftColor = getColorValue(sourceChars.get(leftIndex).style());
        Integer rightColor = getColorValue(sourceChars.get(rightIndex).style());

        if (leftIndex == rightIndex) {
            return leftColor != null ? leftColor : rightColor;
        }

        if (leftColor == null && rightColor == null) return null;
        if (leftColor == null) return rightColor;
        if (rightColor == null) return leftColor;

        double t = sourcePos - leftIndex;

        int lr = (leftColor >> 16) & 0xFF;
        int lg = (leftColor >> 8) & 0xFF;
        int lb = leftColor & 0xFF;

        int rr = (rightColor >> 16) & 0xFF;
        int rg = (rightColor >> 8) & 0xFF;
        int rb = rightColor & 0xFF;

        int r = (int) Math.round(lr + (rr - lr) * t);
        int g = (int) Math.round(lg + (rg - lg) * t);
        int b = (int) Math.round(lb + (rb - lb) * t);

        return (r << 16) | (g << 8) | b;
    }

    private static Integer getColorValue(Style style) {
        if (style == null || style.getColor() == null) return null;
        return style.getColor().getValue();
    }

    private static Style overlayFormatting(Style base, Style formattingOnly) {
        Style result = base != null ? base : Style.EMPTY;
        if (formattingOnly == null || formattingOnly.equals(Style.EMPTY)) {
            return result;
        }

        if (Boolean.TRUE.equals(formattingOnly.isBold())) {
            result = result.withBold(true);
        }
        if (Boolean.TRUE.equals(formattingOnly.isItalic())) {
            result = result.withItalic(true);
        }
        if (Boolean.TRUE.equals(formattingOnly.isUnderlined())) {
            result = result.withUnderlined(true);
        }
        if (Boolean.TRUE.equals(formattingOnly.isStrikethrough())) {
            result = result.withStrikethrough(true);
        }
        if (Boolean.TRUE.equals(formattingOnly.isObfuscated())) {
            result = result.withObfuscated(true);
        }

        return result;
    }

    /** Returns true for formatting (non-color) legacy codes: l, o, n, m, k. */
    private static boolean isFormattingCode(char code) {
        return code == 'l'
                || code == 'o'
                || code == 'n'
                || code == 'm'
                || code == 'k';
    }

    /** Applies only formatting flags (bold, italic, etc.) to the style. */
    private static Style applyFormattingOnly(Style style, char code) {
        return switch (code) {
            case 'l' -> style.withBold(true);
            case 'o' -> style.withItalic(true);
            case 'n' -> style.withUnderlined(true);
            case 'm' -> style.withStrikethrough(true);
            case 'k' -> style.withObfuscated(true);
            default -> style;
        };
    }

    private static boolean isLegacySectionCode(char code) {
        return (code >= '0' && code <= '9')
                || (code >= 'a' && code <= 'f')
                || (code >= 'k' && code <= 'o')
                || code == 'r';
    }

    private static boolean isHexDigit(char c) {
        char lower = Character.toLowerCase(c);
        return (lower >= '0' && lower <= '9')
                || (lower >= 'a' && lower <= 'f');
    }

    private static int parseSectionHexColorAt(String raw, int index) {
        if (raw == null || index < 0 || index + 13 >= raw.length()) return -1;
        if (raw.charAt(index) != '§') return -1;
        if (Character.toLowerCase(raw.charAt(index + 1)) != 'x') return -1;

        StringBuilder hex = new StringBuilder(6);
        for (int off = 2; off < 14; off += 2) {
            if (raw.charAt(index + off) != '§') return -1;

            char digit = raw.charAt(index + off + 1);
            if (!isHexDigit(digit)) return -1;

            hex.append(digit);
        }

        try {
            return Integer.parseInt(hex.toString(), 16);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Style extractFirstStyle(Component text) {
        List<StyledChar> chars = extractStyledChars(text);
        for (StyledChar c : chars) {
            if (c.style() != null && !c.style().equals(Style.EMPTY)) {
                return c.style();
            }
        }
        return Style.EMPTY;
    }

    private static Style applyLegacy(Style style, char code) {
        return switch (code) {
            case '0' -> style.withColor(TextColor.fromRgb(0x000000));
            case '1' -> style.withColor(TextColor.fromRgb(0x0000AA));
            case '2' -> style.withColor(TextColor.fromRgb(0x00AA00));
            case '3' -> style.withColor(TextColor.fromRgb(0x00AAAA));
            case '4' -> style.withColor(TextColor.fromRgb(0xAA0000));
            case '5' -> style.withColor(TextColor.fromRgb(0xAA00AA));
            case '6' -> style.withColor(TextColor.fromRgb(0xFFAA00));
            case '7' -> style.withColor(TextColor.fromRgb(0xAAAAAA));
            case '8' -> style.withColor(TextColor.fromRgb(0x555555));
            case '9' -> style.withColor(TextColor.fromRgb(0x5555FF));
            case 'a' -> style.withColor(TextColor.fromRgb(0x55FF55));
            case 'b' -> style.withColor(TextColor.fromRgb(0x55FFFF));
            case 'c' -> style.withColor(TextColor.fromRgb(0xFF5555));
            case 'd' -> style.withColor(TextColor.fromRgb(0xFF55FF));
            case 'e' -> style.withColor(TextColor.fromRgb(0xFFFF55));
            case 'f' -> style.withColor(TextColor.fromRgb(0xFFFFFF));
            case 'l' -> style.withBold(true);
            case 'o' -> style.withItalic(true);
            case 'n' -> style.withUnderlined(true);
            case 'm' -> style.withStrikethrough(true);
            case 'k' -> style.withObfuscated(true);
            case 'r' -> Style.EMPTY;
            default -> style;
        };
    }
}