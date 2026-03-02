package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.config.StorageConfig;
import dev.dasuro.customnickname.util.ColorParser;
import dev.dasuro.customnickname.util.StyledSegment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.*;

@Mixin(InGameHud.class)
public abstract class ScoreboardHudMixin {

    /**
     * Build a lookup: playerName -> PlayerListEntry (so we can get UUID + Team).
     */
    @Unique
    private static Map<String, PlayerListEntry> customnickname$buildPlayerLookup() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return Map.of();
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return Map.of();

        Map<String, PlayerListEntry> map = new HashMap<>();
        for (PlayerListEntry entry : handler.getPlayerList()) {
            if (entry == null || entry.getProfile() == null) continue;
            String name = entry.getProfile().name();
            if (name != null && !name.isBlank()) {
                map.put(name, entry);
            }
        }
        return map;
    }

    @Redirect(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V"
            )
    )
    private void customnickname$replaceSidebarLine_drawText(
            DrawContext context,
            TextRenderer textRenderer,
            Text text,
            int x,
            int y,
            int color,
            boolean shadow
    ) {
        Text replaced = customnickname$replaceNamesInLine(text);
        context.drawText(textRenderer, replaced, x, y, color, shadow);
    }

    /**
     * Core replacement logic – works like the TabList mixin:
     * Find a player name in the flattened text, replace it with the nickname,
     * and handle Team prefix/suffix based on NickEntry settings.
     */
    @Unique
    private static Text customnickname$replaceNamesInLine(Text original) {
        if (original == null) return Text.empty();

        // Flatten the entire Text tree into a list of styled segments
        List<StyledSegment> segments = new ArrayList<>();
        customnickname$flattenText(original, segments);

        if (segments.isEmpty()) return original;

        // Build the full raw string
        StringBuilder rawBuilder = new StringBuilder();
        for (StyledSegment seg : segments) {
            rawBuilder.append(seg.text);
        }
        String raw = rawBuilder.toString();
        if (raw.isBlank()) return original;

        Map<String, PlayerListEntry> playerLookup = customnickname$buildPlayerLookup();
        if (playerLookup.isEmpty()) return original;

        // Find the best (longest) player name match in the raw string
        String bestName = null;
        PlayerListEntry bestEntry = null;
        int bestStart = -1;
        int bestEnd = -1;

        for (Map.Entry<String, PlayerListEntry> e : playerLookup.entrySet()) {
            String name = e.getKey();
            if (name == null || name.isBlank()) continue;

            int idx = raw.indexOf(name);
            while (idx >= 0) {
                int end = idx + name.length();
                boolean leftOk = idx == 0 || !customnickname$isNameChar(raw.charAt(idx - 1));
                boolean rightOk = end >= raw.length() || !customnickname$isNameChar(raw.charAt(end));

                if (leftOk && rightOk) {
                    if (bestName == null || name.length() > bestName.length()) {
                        bestName = name;
                        bestEntry = e.getValue();
                        bestStart = idx;
                        bestEnd = end;
                    }
                }
                idx = raw.indexOf(name, idx + 1);
            }
        }

        if (bestEntry == null) return original;

        UUID uuid = bestEntry.getProfile().id();
        NickEntry nickEntry = NickConfig.get(uuid);
        if (nickEntry == null) return original;

        Team team = bestEntry.getScoreboardTeam();
        String beforeName = raw.substring(0, bestStart);
        String afterName = raw.substring(bestEnd);

        // Determine team prefix/suffix strings
        String teamPrefixStr = "";
        String teamSuffixStr = "";
        if (team != null) {
            teamPrefixStr = team.getPrefix().getString();
            teamSuffixStr = team.getSuffix().getString();
        }

        MutableText result = Text.empty();

        // Handle prefix area
        if (!beforeName.isEmpty()) {
            if (nickEntry.showPrefix) {
                // Reconstruct the before-name part with original styles
                result.append(customnickname$reconstructRange(segments, 0, bestStart));
            } else {
                // Strip the team prefix, keep any remaining non-prefix text
                String nonPrefix = customnickname$removeTeamPart(beforeName, teamPrefixStr);
                if (!nonPrefix.isEmpty()) {
                    int nonPrefixStart = bestStart - nonPrefix.length();
                    if (nonPrefixStart >= 0) {
                        result.append(customnickname$reconstructRange(segments, nonPrefixStart, bestStart));
                    }
                }
            }
        }

        // The nickname itself
        Text baseName = Text.literal(bestName);
        if (team != null && team.getColor().getColorValue() != null) {
            baseName = Text.literal(bestName).setStyle(
                    net.minecraft.text.Style.EMPTY.withColor(net.minecraft.text.TextColor.fromRgb(team.getColor().getColorValue()))
            );
        }
        result.append(ColorParser.buildNick(nickEntry, baseName));

        if (StorageConfig.isShowIndicator()) {
            result.append(Text.literal(StorageConfig.INDICATOR).styled(s -> s.withColor(0xFFFF00)));
        }

        // Handle suffix area
        if (!afterName.isEmpty()) {
            if (nickEntry.showSuffix) {
                result.append(customnickname$reconstructRange(segments, bestEnd, raw.length()));
            } else {
                // Strip team suffix from afterName, keep the rest (e.g. score)
                String nonSuffix = customnickname$removeTeamPart(afterName, teamSuffixStr);
                if (!nonSuffix.isEmpty()) {
                    // The non-suffix part is at the end of afterName
                    int nonSuffixStart = raw.length() - nonSuffix.length();
                    if (nonSuffixStart >= bestEnd) {
                        result.append(customnickname$reconstructRange(segments, nonSuffixStart, raw.length()));
                    }
                }
            }
        }

        return result;
    }

    /**
     * Remove the team prefix/suffix text from the beginning of a string.
     * Returns whatever remains.
     */
    @Unique
    private static String customnickname$removeTeamPart(String text, String teamPart) {
        if (teamPart == null || teamPart.isEmpty()) return text;
        if (text.startsWith(teamPart)) {
            return text.substring(teamPart.length());
        }
        // Trim and try again (sometimes there are extra spaces)
        String trimmedTeam = teamPart.trim();
        if (!trimmedTeam.isEmpty() && text.startsWith(trimmedTeam)) {
            return text.substring(trimmedTeam.length());
        }
        return text;
    }

    /**
     * Flatten a Text tree into a list of styled segments (literal text + style pairs).
     */
    @Unique
    private static void customnickname$flattenText(Text text, List<StyledSegment> out) {
        String literal = "";
        if (text.getContent() instanceof PlainTextContent plain) {
            literal = plain.string();
        }
        if (!literal.isEmpty()) {
            out.add(new StyledSegment(literal, text.getStyle()));
        }
        for (Text sibling : text.getSiblings()) {
            customnickname$flattenText(sibling, out);
        }
    }

    /**
     * Reconstruct a Text from segments for a specific character range [start, end) in the flattened string.
     */
    @Unique
    private static MutableText customnickname$reconstructRange(List<StyledSegment> segments, int start, int end) {
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

    @Unique
    private static boolean customnickname$isNameChar(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '_';
    }
}
