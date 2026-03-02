package dev.dasuro.customnickname.util;

import dev.dasuro.customnickname.config.NickEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.regex.*;

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
    private static final Pattern TRAILING_INCOMPLETE_HEX = Pattern.compile("&#[0-9A-Fa-f]{0,5}$");

    public static MutableText buildNick(NickEntry nick, Text serverOriginal) {
        if (nick == null) return Text.empty();

        String nickname = nick.nickname;
        if (nickname == null) nickname = "";

        if (nick.rainbow) {
            // Parse formatting codes to preserve bold/italic/etc., but ignore colors
            java.util.List<StyledChar> chars = parseToStyledChars(nickname);
            return rainbowWave(chars, System.currentTimeMillis(), nick.rainbowSpeed);
        }
        if (hasActualColorCodes(nickname)) return parse(nickname);
        if (hasFormattingOnlyCodes(nickname)) return parseWithOriginalColor(nickname, serverOriginal);
        return applyOriginalStyle(nickname, serverOriginal);
    }

    public static MutableText parse(String input) {
        if (input == null || input.isEmpty()) return Text.empty();

        // If someone is mid-typing or pasted a cut-off hex code, drop the incomplete tail.
        input = TRAILING_INCOMPLETE_HEX.matcher(input).replaceAll("");

        MutableText result = Text.empty();
        Matcher matcher = CODE_PATTERN.matcher(input);
        int lastEnd = 0;
        Style currentStyle = Style.EMPTY;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                result.append(
                        Text.literal(
                                input.substring(lastEnd, matcher.start())
                        ).setStyle(currentStyle)
                );
            }
            if (matcher.group(1) != null) {
                int rgb = Integer.parseInt(matcher.group(1), 16);
                currentStyle = currentStyle.withColor(
                        TextColor.fromRgb(rgb)
                );
            } else {
                char code = Character.toLowerCase(
                        matcher.group(2).charAt(0)
                );
                currentStyle = applyLegacy(currentStyle, code);
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < input.length()) {
            result.append(
                    Text.literal(input.substring(lastEnd))
                            .setStyle(currentStyle)
            );
        }
        return result;
    }


    /** Number of characters for one full rainbow cycle. */
    private static final double RAINBOW_WAVELENGTH = 20.0;

    /**
     * A single visible character together with its non-color style
     * (bold, italic, underline, strikethrough, obfuscated).
     */
    private record StyledChar(String character, Style baseStyle) {}

    /**
     * Parses formatting codes from the nickname and returns one StyledChar per
     * visible character.  Color codes (&amp;0-f, &amp;#RRGGBB) are stripped because the
     * rainbow supplies its own color, but formatting codes (&amp;l, &amp;o, &amp;n, &amp;m, &amp;k)
     * are kept.  &amp;r resets only the formatting flags (not relevant for color
     * since rainbow overrides it anyway).
     */
    private static java.util.List<StyledChar> parseToStyledChars(String input) {
        java.util.List<StyledChar> result = new java.util.ArrayList<>();
        if (input == null || input.isEmpty()) return result;

        // Remove trailing incomplete hex codes
        input = TRAILING_INCOMPLETE_HEX.matcher(input).replaceAll("");

        Matcher matcher = CODE_PATTERN.matcher(input);
        int lastEnd = 0;
        // Only track non-color formatting
        Style formatting = Style.EMPTY;

        while (matcher.find()) {
            // Collect visible characters before this code
            if (matcher.start() > lastEnd) {
                String segment = input.substring(lastEnd, matcher.start());
                int[] cps = segment.codePoints().toArray();
                for (int cp : cps) {
                    result.add(new StyledChar(new String(Character.toChars(cp)), formatting));
                }
            }

            if (matcher.group(1) == null) {
                // Legacy code (not hex) – check for formatting vs color
                char code = Character.toLowerCase(matcher.group(2).charAt(0));
                if (code == 'r') {
                    // Reset: clear all formatting
                    formatting = Style.EMPTY;
                } else if (isFormattingCode(code)) {
                    // Formatting code (bold, italic, etc.) – apply
                    formatting = applyFormattingOnly(formatting, code);
                }
                // Legacy color codes (0-9, a-f) are ignored for rainbow
            }
            // Hex color codes are also ignored for rainbow
            lastEnd = matcher.end();
        }

        // Remaining visible characters after last code
        if (lastEnd < input.length()) {
            String tail = input.substring(lastEnd);
            int[] cps = tail.codePoints().toArray();
            for (int cp : cps) {
                result.add(new StyledChar(new String(Character.toChars(cp)), formatting));
            }
        }

        return result;
    }

    /** Returns true for formatting (non-color) legacy codes: l, o, n, m, k. */
    private static boolean isFormattingCode(char code) {
        return code == 'l' || code == 'o' || code == 'n' || code == 'm' || code == 'k';
    }

    /** Applies only formatting flags (bold, italic, etc.) to the style. */
    private static Style applyFormattingOnly(Style style, char code) {
        return switch (code) {
            case 'l' -> style.withBold(true);
            case 'o' -> style.withItalic(true);
            case 'n' -> style.withUnderline(true);
            case 'm' -> style.withStrikethrough(true);
            case 'k' -> style.withObfuscated(true);
            default  -> style;
        };
    }

    /**
     * Builds a rainbow-wave text from pre-parsed styled characters.
     * Each character keeps its formatting (bold, italic, etc.) but gets
     * its color from the rainbow wave.
     */
    private static MutableText rainbowWave(java.util.List<StyledChar> chars, long timeMs, float speed) {
        MutableText result = Text.empty();
        if (chars == null || chars.isEmpty()) return result;

        int len = chars.size();

        // Time-based offset – negative so the wave travels right → left
        double offset = -(timeMs / 1000.0) * speed * 0.25;

        for (int i = 0; i < len; i++) {
            double hue = (i / RAINBOW_WAVELENGTH + offset) % 1.0;
            if (hue < 0) hue += 1.0;

            int rgb = java.awt.Color.HSBtoRGB((float) hue, 1.0f, 1.0f) & 0xFFFFFF;

            StyledChar sc = chars.get(i);
            // Start from the character's formatting style, then apply rainbow color
            Style style = sc.baseStyle().withColor(TextColor.fromRgb(rgb));

            result.append(Text.literal(sc.character()).setStyle(style));
        }

        return result;
    }

    public static MutableText applyOriginalStyle(
            String newText,
            Text original
    ) {
        Style style = extractFirstStyle(original);
        return Text.literal(newText).setStyle(style);
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
     * Parses formatting codes (bold, italic, etc.) and &amp;r resets from the nickname,
     * but keeps the original player color from the server. This is used when the
     * nickname contains only formatting codes and no explicit color codes.
     */
    public static MutableText parseWithOriginalColor(String input, Text serverOriginal) {
        if (input == null || input.isEmpty()) return Text.empty();

        Style originalStyle = extractFirstStyle(serverOriginal);

        // Remove trailing incomplete hex codes
        input = TRAILING_INCOMPLETE_HEX.matcher(input).replaceAll("");

        MutableText result = Text.empty();
        Matcher matcher = CODE_PATTERN.matcher(input);
        int lastEnd = 0;
        // Start with the original style (preserves color from the server)
        Style currentStyle = originalStyle;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                result.append(
                        Text.literal(
                                input.substring(lastEnd, matcher.start())
                        ).setStyle(currentStyle)
                );
            }
            if (matcher.group(2) != null) {
                char code = Character.toLowerCase(matcher.group(2).charAt(0));
                if (code == 'r') {
                    // Reset: go back to the original style (not Style.EMPTY)
                    currentStyle = originalStyle;
                } else if (isFormattingCode(code)) {
                    currentStyle = applyFormattingOnly(currentStyle, code);
                }
                // Ignore color codes here (shouldn't be present, but just in case)
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < input.length()) {
            result.append(
                    Text.literal(input.substring(lastEnd))
                            .setStyle(currentStyle)
            );
        }
        return result;
    }

    public static String strip(String input) {
        if (input == null || input.isEmpty()) return "";
        return input.replaceAll(
                "&#[0-9A-Fa-f]{6}|&[0-9a-fk-orA-FK-OR]", ""
        );
    }

    private static Style extractFirstStyle(Text text) {
        Style s = text.getStyle();
        if (s != null && s != Style.EMPTY) return s;
        for (Text sibling : text.getSiblings()) {
            Style found = extractFirstStyle(sibling);
            if (found != Style.EMPTY) return found;
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
            case 'n' -> style.withUnderline(true);
            case 'm' -> style.withStrikethrough(true);
            case 'k' -> style.withObfuscated(true);
            case 'r' -> Style.EMPTY;
            default  -> style;
        };
    }
}