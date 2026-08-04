package dev.dasuro.customnickname.util;

import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.config.StorageConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class NickDisplayBuilder {

    private NickDisplayBuilder() {
    }

    public static MutableComponent buildStyledBaseName(
            String currentName,
            Component styleSource,
            PlayerTeam team
    ) {
        String safeName = currentName != null ? currentName : "";

        MutableComponent styledName = extractStyledNameComponent(
                styleSource,
                safeName
        );
        if (styledName != null && !styledName.getString().isEmpty()) {
            return styledName;
        }

        if (team != null) {
            return team.getColor()
                    .<MutableComponent>map(color ->
                            Component.literal(safeName)
                                    .withStyle(style ->
                                            style.withColor(
                                                    color.textColor()
                                            )
                                    )
                    )
                    .orElseGet(() -> Component.literal(safeName));
        }

        return Component.literal(safeName);
    }

    public static MutableComponent buildDisplay(
            NickEntry nick,
            PlayerTeam team,
            MutableComponent nickComponent
    ) {
        return buildDisplay(nick, team, nickComponent, null);
    }

    public static MutableComponent buildDisplay(
            NickEntry nick,
            PlayerTeam team,
            MutableComponent nickComponent,
            MutableComponent serverOriginalName
    ) {
        MutableComponent full = Component.empty();

        if (team != null && nick.showPrefix) {
            full.append(team.getPlayerPrefix());
        }

        full.append(nickComponent);

        if (team != null && nick.showSuffix) {
            full.append(team.getPlayerSuffix());
        }

        appendServerColorMarker(full, nick, serverOriginalName, team);

        if (StorageConfig.isShowIndicator()) {
            full.append(
                    Component.literal(StorageConfig.INDICATOR)
                            .withColor(0xFFFF00)
            );
        }

        return full;
    }

    public static MutableComponent replaceInOriginalOrFallback(
            MutableComponent original,
            String currentName,
            NickEntry nick,
            PlayerTeam team,
            boolean keepOriginalWhenNameNotFound,
            boolean strictHideAffixesWhenDisabled
    ) {
        if (currentName == null || currentName.isBlank()) {
            return buildDisplay(
                    nick,
                    team,
                    ColorParser.buildNick(nick, Component.literal(""))
            );
        }

        if (isAlreadyProcessed(original)) {
            return original.copy();
        }

        boolean allowMissingTeamAffixesFallback = !keepOriginalWhenNameNotFound;
        MutableComponent replaced = replaceInsideOriginal(
                original,
                currentName,
                nick,
                team,
                allowMissingTeamAffixesFallback,
                strictHideAffixesWhenDisabled
        );
        if (replaced != null) {
            return replaced;
        }

        if (keepOriginalWhenNameNotFound && original != null) {
            if (strictHideAffixesWhenDisabled
                    && (!nick.showPrefix || !nick.showSuffix)) {
                MutableComponent baseName = buildStyledBaseName(
                        currentName,
                        original,
                        team
                );
                MutableComponent nickComponent = ColorParser.buildNick(
                        nick,
                        baseName
                );
                return buildDisplay(nick, team, nickComponent, baseName);
            }
            return original;
        }

        MutableComponent baseName = buildStyledBaseName(
                currentName,
                original,
                team
        );
        MutableComponent nickComponent = ColorParser.buildNick(nick, baseName);

        return buildDisplay(nick, team, nickComponent, baseName);
    }

    private static MutableComponent replaceInsideOriginal(
            MutableComponent original,
            String currentName,
            NickEntry nick,
            PlayerTeam team,
            boolean allowMissingTeamAffixesFallback,
            boolean strictHideAffixesWhenDisabled
    ) {
        if (original == null || currentName == null || currentName.isBlank()) {
            return null;
        }

        List<StyledSegment> segments = new ArrayList<>();
        flattenText(original, segments);
        if (segments.isEmpty()) return null;

        StringBuilder rawBuilder = new StringBuilder();
        for (StyledSegment seg : segments) {
            rawBuilder.append(seg.text());
        }

        String raw = rawBuilder.toString();
        if (raw.isEmpty()) return null;

        int start = findStandaloneName(raw, currentName);
        if (start < 0) return null;
        int end = start + currentName.length();

        MutableComponent baseName;
        MutableComponent extractedName = reconstructRange(segments, start, end);
        if (extractedName != null && !extractedName.getString().isEmpty()) {
            baseName = extractedName;
        } else {
            baseName = buildStyledBaseName(currentName, null, team);
        }

        String beforeName = raw.substring(0, start);
        String afterName = raw.substring(end);
        String teamPrefix = team != null
                ? team.getPlayerPrefix().getString()
                : "";
        String teamSuffix = team != null
                ? team.getPlayerSuffix().getString()
                : "";

        MutableComponent result = Component.empty();

        if (nick.showPrefix) {
            if (!beforeName.isEmpty()) {
                result.append(reconstructRange(segments, 0, start));
            } else if (allowMissingTeamAffixesFallback
                    && team != null
                    && !teamPrefix.isEmpty()) {
                result.append(team.getPlayerPrefix());
            }
        } else if (!beforeName.isEmpty() && !strictHideAffixesWhenDisabled) {
            String nonPrefix = removeLeadingTeamPart(beforeName, teamPrefix);
            if (!nonPrefix.isEmpty()) {
                int nonPrefixStart = start - nonPrefix.length();
                if (nonPrefixStart >= 0) {
                    result.append(
                            reconstructRange(segments, nonPrefixStart, start)
                    );
                }
            }
        }

        result.append(ColorParser.buildNick(nick, baseName));

        if (nick.showSuffix) {
            if (!afterName.isEmpty()) {
                result.append(reconstructRange(segments, end, raw.length()));
            } else if (allowMissingTeamAffixesFallback
                    && team != null
                    && !teamSuffix.isEmpty()) {
                result.append(team.getPlayerSuffix());
            }
        } else if (!afterName.isEmpty() && !strictHideAffixesWhenDisabled) {
            String nonSuffix = removeLeadingTeamPart(afterName, teamSuffix);
            if (!nonSuffix.isEmpty()) {
                int nonSuffixStart = raw.length() - nonSuffix.length();
                if (nonSuffixStart >= end) {
                    result.append(
                            reconstructRange(
                                    segments,
                                    nonSuffixStart,
                                    raw.length()
                            )
                    );
                }
            }
        }

        appendServerColorMarker(result, nick, baseName, team);

        if (StorageConfig.isShowIndicator()) {
            result.append(
                    Component.literal(StorageConfig.INDICATOR)
                            .withColor(0xFFFF00)
            );
        }

        return result;
    }

    private static String removeLeadingTeamPart(String text, String teamPart) {
        if (text == null
                || text.isEmpty()
                || teamPart == null
                || teamPart.isEmpty()) {
            return text;
        }

        if (text.startsWith(teamPart)) {
            return text.substring(teamPart.length());
        }

        String trimmedTeam = teamPart.trim();
        if (!trimmedTeam.isEmpty() && text.startsWith(trimmedTeam)) {
            return text.substring(trimmedTeam.length());
        }

        int ws = 0;
        while (ws < text.length() && Character.isWhitespace(text.charAt(ws))) {
            ws++;
        }

        if (ws > 0) {
            String noLeadingWs = text.substring(ws);
            if (noLeadingWs.startsWith(teamPart)) {
                return noLeadingWs.substring(teamPart.length());
            }
            if (!trimmedTeam.isEmpty() && noLeadingWs.startsWith(trimmedTeam)) {
                return noLeadingWs.substring(trimmedTeam.length());
            }
        }

        return text;
    }

    private static int findStandaloneName(String raw, String name) {
        String rawLower = raw.toLowerCase(Locale.ROOT);
        String nameLower = name.toLowerCase(Locale.ROOT);

        int index = rawLower.indexOf(nameLower);
        while (index >= 0) {
            int end = index + name.length();
            boolean leftOk = isLeftBoundary(raw, index);
            boolean rightOk = isRightBoundary(raw, end);
            if (leftOk && rightOk) return index;
            index = rawLower.indexOf(nameLower, index + 1);
        }
        return -1;
    }

    private static boolean isLeftBoundary(String raw, int index) {
        return index <= 0 || !isNameChar(raw.charAt(index - 1));
    }

    private static boolean isRightBoundary(String raw, int index) {
        return index >= raw.length() || !isNameChar(raw.charAt(index));
    }

    private static boolean isNameChar(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '_';
    }

    private static void flattenText(Component text, List<StyledSegment> out) {
        flattenText(text, Style.EMPTY, out);
    }

    private static void flattenText(
            Component text,
            Style inheritedStyle,
            List<StyledSegment> out
    ) {
        if (text == null) return;

        Style effectiveStyle = resolveStyle(inheritedStyle, text.getStyle());

        if (text.getContents() instanceof PlainTextContents plain) {
            appendFormattedLiteral(plain.text(), effectiveStyle, out);
        } else if (text.getContents() instanceof TranslatableContents tc) {
            Object[] args = tc.getArgs();
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof Component argText) {
                        flattenText(argText, effectiveStyle, out);
                    } else if (arg instanceof String argStr
                            && !argStr.isEmpty()) {
                        appendFormattedLiteral(argStr, effectiveStyle, out);
                    }
                }
            }
        } else {
            String fallback = text.plainCopy().getString();
            if (fallback != null && !fallback.isEmpty()) {
                appendFormattedLiteral(fallback, effectiveStyle, out);
            }
        }

        for (Component sibling : text.getSiblings()) {
            flattenText(sibling, effectiveStyle, out);
        }
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

    private static void appendFormattedLiteral(
            String raw,
            Style baseStyle,
            List<StyledSegment> out
    ) {
        if (raw == null || raw.isEmpty()) return;

        Style current = baseStyle != null ? baseStyle : Style.EMPTY;

        if (raw.indexOf('§') == -1) {
            out.add(new StyledSegment(raw, current));
            return;
        }

        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            if (raw.charAt(i) == '§') {
                int hexColor = parseHexColorAt(raw, i);
                if (hexColor >= 0) {
                    flushStyledSegment(out, buf, current);
                    current = current.withColor(TextColor.fromRgb(hexColor));
                    i += 14;
                    continue;
                }

                if (i + 1 < raw.length()) {
                    char code = Character.toLowerCase(raw.charAt(i + 1));
                    if (isLegacySectionCode(code)) {
                        flushStyledSegment(out, buf, current);
                        current = applyLegacyCode(current, code);
                        i += 2;
                        continue;
                    }
                }
            }

            buf.append(raw.charAt(i));
            i++;
        }

        flushStyledSegment(out, buf, current);
    }

    private static void flushStyledSegment(
            List<StyledSegment> out,
            StringBuilder buf,
            Style style
    ) {
        if (buf.length() == 0) return;
        out.add(new StyledSegment(buf.toString(), style));
        buf.setLength(0);
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

    private static int parseHexColorAt(String raw, int index) {
        if (raw == null || index < 0 || index + 13 >= raw.length()) {
            return -1;
        }
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

    private static Style applyLegacyCode(Style style, char code) {
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

    private static MutableComponent extractStyledNameComponent(
            Component text,
            String currentName
    ) {
        if (text == null || currentName == null || currentName.isBlank()) {
            return null;
        }

        List<StyledSegment> segments = new ArrayList<>();
        flattenText(text, segments);
        if (segments.isEmpty()) return null;

        StringBuilder rawBuilder = new StringBuilder();
        for (StyledSegment seg : segments) {
            rawBuilder.append(seg.text());
        }

        String raw = rawBuilder.toString();
        if (raw.isEmpty()) return null;

        int start = findStandaloneName(raw, currentName);
        if (start < 0) return null;

        return reconstructRange(segments, start, start + currentName.length());
    }

    private static MutableComponent reconstructRange(
            List<StyledSegment> segments,
            int start,
            int end
    ) {
        MutableComponent result = Component.empty();
        int pos = 0;

        for (StyledSegment seg : segments) {
            int segStart = pos;
            int segEnd = pos + seg.text().length();
            pos = segEnd;

            if (segEnd <= start) continue;
            if (segStart >= end) break;

            int from = Math.max(start - segStart, 0);
            int to = Math.min(end - segStart, seg.text().length());
            if (from < to) {
                result.append(
                        Component.literal(seg.text().substring(from, to))
                                .setStyle(seg.style())
                );
            }
        }

        return result;
    }

    private static Style extractFirstStyle(Component text) {
        if (text == null) return Style.EMPTY;

        List<StyledSegment> segments = new ArrayList<>();
        flattenText(text, segments);

        for (StyledSegment seg : segments) {
            if (!seg.text().isEmpty()
                    && seg.style() != null
                    && !seg.style().equals(Style.EMPTY)) {
                return seg.style();
            }
        }

        return Style.EMPTY;
    }

    public static boolean shouldShowServerColorMarker(NickEntry nick) {
        if (nick == null || !StorageConfig.isShowServerColorMarker()) {
            return false;
        }
        if (nick.rainbow) return true;
        return ColorParser.hasActualColorCodes(nick.nickname);
    }

    public static void appendServerColorMarker(
            MutableComponent out,
            NickEntry nick,
            Component serverOriginalName,
            PlayerTeam team
    ) {
        if (out == null || !shouldShowServerColorMarker(nick)) return;

        Integer color = null;

        Style nameStyle = extractFirstStyle(serverOriginalName);
        if (nameStyle != null
                && nameStyle != Style.EMPTY
                && nameStyle.getColor() != null) {
            color = nameStyle.getColor().getValue();
        }

        if (color == null && team != null) {
            color = team.getColor()
                    .map(teamColor -> teamColor.textColor())
                    .filter(Objects::nonNull)
                    .map(textColor -> textColor.getValue())
                    .orElse(null);
        }

        MutableComponent marker = Component.literal(
                StorageConfig.SERVER_COLOR_MARKER
        );
        if (color != null) {
            marker = marker.withColor(color);
        }

        out.append(marker);
    }

    private static boolean isAlreadyProcessed(Component text) {
        if (text == null) return false;

        String raw = text.getString();
        if (raw == null || raw.isEmpty()) return false;

        String indicator = StorageConfig.INDICATOR;
        if (indicator != null && !indicator.isBlank()) {
            String trimmed = indicator.trim();
            if (!trimmed.isEmpty() && raw.contains(trimmed)) {
                return true;
            }
        }

        String marker = StorageConfig.SERVER_COLOR_MARKER;
        if (marker != null && !marker.isBlank()) {
            String trimmed = marker.trim();
            if (!trimmed.isEmpty() && raw.contains(trimmed)) {
                return true;
            }
        }

        return false;
    }

    private record StyledSegment(String text, Style style) {
    }
}