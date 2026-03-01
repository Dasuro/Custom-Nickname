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

    /** Matches a trailing, incomplete hex color introducer that can happen while typing/pasting. */
    private static final Pattern TRAILING_INCOMPLETE_HEX = Pattern.compile("&#[0-9A-Fa-f]{0,5}$");

    public static MutableText buildNick(NickEntry nick, Text serverOriginal) {
        if (nick == null) return Text.empty();

        String nickname = nick.nickname;
        if (nickname == null) nickname = "";

        if (nick.rainbow) {
            return rainbowWave(strip(nickname), System.currentTimeMillis(),
                    nick.rainbowSpeed);
        }
        if (hasColorCodes(nickname)) return parse(nickname);
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


    public static MutableText rainbowWave(String plain, long timeMs, float speed) {
        MutableText result = Text.empty();
        int len = plain.length();
        if (len == 0) return result;

        double t = (timeMs / 1000.0) * speed;

        // Base hue moves slowly over time
        double baseHue = (t * 0.08) % 1.0;

        // Small hue wave traveling through the text
        double wavelength = 12.0;
        double k = (Math.PI * 2.0) / wavelength;
        double phase = t * 2.0 * Math.PI;

        // Keep subtle so it looks like one wave, not random per letter
        double amplitude = 0.12;

        for (int i = 0; i < len; i++) {
            double hue =
                    (baseHue + amplitude * Math.sin(k * i - phase)) % 1.0;
            if (hue < 0) hue += 1.0;

            int rgb =
                    java.awt.Color.HSBtoRGB((float) hue, 1.0f, 1.0f) & 0xFFFFFF;

            result.append(
                    Text.literal(String.valueOf(plain.charAt(i)))
                            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)))
            );
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