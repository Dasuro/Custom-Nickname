package dev.dasuro.customnickname.util;

import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.config.StorageConfig;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NickDisplayBuilder {

    private NickDisplayBuilder() {
    }

    public static Text buildStyledBaseName(String currentName, Text styleSource, Team team) {
        String safeName = currentName != null ? currentName : "";

        Style sourceStyle = extractFirstStyle(styleSource);
        if (sourceStyle != Style.EMPTY) {
            return Text.literal(safeName).setStyle(sourceStyle);
        }

        if (team != null && team.getColor() != null && team.getColor().getColorValue() != null) {
            return Text.literal(safeName).styled(s -> s.withColor(team.getColor().getColorValue()));
        }

        return Text.literal(safeName);
    }

    public static MutableText buildDisplay(NickEntry nick, Team team, MutableText nickComponent) {
        MutableText full = Text.empty();

        if (team != null && nick.showPrefix) {
            full.append(team.getPrefix());
        }

        full.append(nickComponent);

        if (team != null && nick.showSuffix) {
            full.append(team.getSuffix());
        }

        if (StorageConfig.isShowIndicator()) {
            full.append(Text.literal(StorageConfig.INDICATOR).styled(s -> s.withColor(0xFFFF00)));
        }

        return full;
    }

    public static Text replaceInOriginalOrFallback(Text original, String currentName, NickEntry nick, Team team) {
        return replaceInOriginalOrFallback(original, currentName, nick, team, false);
    }

    public static Text replaceInOriginalOrFallback(
            Text original,
            String currentName,
            NickEntry nick,
            Team team,
            boolean keepOriginalWhenNameNotFound
    ) {
        return replaceInOriginalOrFallback(original, currentName, nick, team, keepOriginalWhenNameNotFound, false);
    }

    public static Text replaceInOriginalOrFallback(
            Text original,
            String currentName,
            NickEntry nick,
            Team team,
            boolean keepOriginalWhenNameNotFound,
            boolean strictHideAffixesWhenDisabled
    ) {
        if (currentName == null || currentName.isBlank()) {
            return buildDisplay(nick, team, ColorParser.buildNick(nick, Text.literal("")));
        }

        boolean allowMissingTeamAffixesFallback = !keepOriginalWhenNameNotFound;
        Text replaced = replaceInsideOriginal(
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
            if (strictHideAffixesWhenDisabled && (!nick.showPrefix || !nick.showSuffix)) {
                Text baseName = buildStyledBaseName(currentName, original, team);
                MutableText nickComponent = ColorParser.buildNick(nick, baseName);
                return buildDisplay(nick, team, nickComponent);
            }
            return original;
        }

        Text baseName = buildStyledBaseName(currentName, original, team);
        MutableText nickComponent = ColorParser.buildNick(nick, baseName);

        return buildDisplay(nick, team, nickComponent);
    }

    private static Text replaceInsideOriginal(
            Text original,
            String currentName,
            NickEntry nick,
            Team team,
            boolean allowMissingTeamAffixesFallback,
            boolean strictHideAffixesWhenDisabled
    ) {
        if (original == null || currentName == null || currentName.isBlank()) return null;

        List<StyledSegment> segments = new ArrayList<>();
        flattenText(original, segments);
        if (segments.isEmpty()) return null;

        StringBuilder rawBuilder = new StringBuilder();
        for (StyledSegment seg : segments) rawBuilder.append(seg.text);
        String raw = rawBuilder.toString();
        if (raw.isEmpty()) return null;

        int start = findStandaloneName(raw, currentName);
        if (start < 0) return null;
        int end = start + currentName.length();

        Style nameStyle = getStyleAtPosition(segments, start);
        Text baseName;
        if (nameStyle != null && nameStyle != Style.EMPTY) {
            baseName = Text.literal(currentName).setStyle(nameStyle);
        } else {
            baseName = buildStyledBaseName(currentName, null, team);
        }

        String beforeName = raw.substring(0, start);
        String afterName = raw.substring(end);
        String teamPrefix = team != null ? team.getPrefix().getString() : "";
        String teamSuffix = team != null ? team.getSuffix().getString() : "";

        MutableText result = Text.empty();

        if (nick.showPrefix) {
            if (!beforeName.isEmpty()) {
                result.append(reconstructRange(segments, 0, start));
            } else if (allowMissingTeamAffixesFallback && team != null && !teamPrefix.isEmpty()) {
                result.append(team.getPrefix());
            }
        } else if (!beforeName.isEmpty() && !strictHideAffixesWhenDisabled) {
            String nonPrefix = removeLeadingTeamPart(beforeName, teamPrefix);
            if (!nonPrefix.isEmpty()) {
                int nonPrefixStart = start - nonPrefix.length();
                if (nonPrefixStart >= 0) {
                    result.append(reconstructRange(segments, nonPrefixStart, start));
                }
            }
        }

        result.append(ColorParser.buildNick(nick, baseName));

        if (nick.showSuffix) {
            if (!afterName.isEmpty()) {
                result.append(reconstructRange(segments, end, raw.length()));
            } else if (allowMissingTeamAffixesFallback && team != null && !teamSuffix.isEmpty()) {
                result.append(team.getSuffix());
            }
        } else if (!afterName.isEmpty() && !strictHideAffixesWhenDisabled) {
            String nonSuffix = removeLeadingTeamPart(afterName, teamSuffix);
            if (!nonSuffix.isEmpty()) {
                int nonSuffixStart = raw.length() - nonSuffix.length();
                if (nonSuffixStart >= end) {
                    result.append(reconstructRange(segments, nonSuffixStart, raw.length()));
                }
            }
        }

        if (StorageConfig.isShowIndicator()) {
            result.append(Text.literal(StorageConfig.INDICATOR).styled(s -> s.withColor(0xFFFF00)));
        }

        return result;
    }

    private static String removeLeadingTeamPart(String text, String teamPart) {
        if (text == null || text.isEmpty() || teamPart == null || teamPart.isEmpty()) return text;

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
        int pos = index - 1;
        while (pos >= 1 && raw.charAt(pos - 1) == '§' && isLegacyFormatCode(raw.charAt(pos))) {
            pos -= 2;
        }
        return pos < 0 || !isNameChar(raw.charAt(pos));
    }

    private static boolean isRightBoundary(String raw, int index) {
        int pos = index;
        while (pos + 1 < raw.length() && raw.charAt(pos) == '§' && isLegacyFormatCode(raw.charAt(pos + 1))) {
            pos += 2;
        }
        return pos >= raw.length() || !isNameChar(raw.charAt(pos));
    }

    private static boolean isLegacyFormatCode(char c) {
        char code = Character.toLowerCase(c);
        return (code >= '0' && code <= '9')
                || (code >= 'a' && code <= 'f')
                || (code >= 'k' && code <= 'o')
                || code == 'r';
    }

    private static boolean isNameChar(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '_';
    }

    private static void flattenText(Text text, List<StyledSegment> out) {
        if (text.getContent() instanceof PlainTextContent plain) {
            String literal = plain.string();
            if (!literal.isEmpty()) out.add(new StyledSegment(literal, text.getStyle()));
        } else if (text.getContent() instanceof TranslatableTextContent tc) {
            for (Object arg : tc.getArgs()) {
                if (arg instanceof Text argText) {
                    flattenText(argText, out);
                } else if (arg instanceof String argStr && !argStr.isEmpty()) {
                    out.add(new StyledSegment(argStr, text.getStyle()));
                }
            }
        } else {
            String fallback = text.copyContentOnly().getString();
            if (fallback != null && !fallback.isEmpty()) {
                out.add(new StyledSegment(fallback, text.getStyle()));
            }
        }

        for (Text sibling : text.getSiblings()) {
            flattenText(sibling, out);
        }
    }

    private static MutableText reconstructRange(List<StyledSegment> segments, int start, int end) {
        MutableText result = Text.empty();
        int pos = 0;
        for (StyledSegment seg : segments) {
            int segStart = pos;
            int segEnd = pos + seg.text.length();
            pos = segEnd;

            if (segEnd <= start) continue;
            if (segStart >= end) break;

            int from = Math.max(start - segStart, 0);
            int to = Math.min(end - segStart, seg.text.length());
            if (from < to) {
                result.append(Text.literal(seg.text.substring(from, to)).setStyle(seg.style));
            }
        }
        return result;
    }

    private static Style getStyleAtPosition(List<StyledSegment> segments, int position) {
        int pos = 0;
        for (StyledSegment seg : segments) {
            int segEnd = pos + seg.text.length();
            if (position >= pos && position < segEnd) return seg.style;
            pos = segEnd;
        }
        return null;
    }

    private static Style extractFirstStyle(Text text) {
        if (text == null) return Style.EMPTY;

        Style s = text.getStyle();
        if (s != null && s != Style.EMPTY) {
            return s;
        }

        for (Text sibling : text.getSiblings()) {
            Style nested = extractFirstStyle(sibling);
            if (nested != Style.EMPTY) {
                return nested;
            }
        }

        return Style.EMPTY;
    }
}

