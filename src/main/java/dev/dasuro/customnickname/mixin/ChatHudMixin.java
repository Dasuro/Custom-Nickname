package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.config.StorageConfig;
import dev.dasuro.customnickname.util.ColorParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    @Unique
    private static final String MC_NAME_CHARS = "[A-Za-z0-9_]";


    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Text customnickname$onAddMessage(Text original) {
        Text result = replaceNamesInTree(original);

        // If indicator is enabled and something was actually replaced, append ✎ once at the end
        if (StorageConfig.isShowIndicator() && result != original) {
            // Strip any indicator characters that leaked in from other mixins
            // (e.g. PlayerListEntryMixin adding ✎ to display names used as chat arguments)
            MutableText cleaned = stripIndicator(result);
            cleaned.append(Text.literal(StorageConfig.INDICATOR).styled(s -> s.withColor(0xFFFF00)));
            return cleaned;
        }

        return result;
    }

    /**
     * Recursively strips all occurrences of the indicator string from a Text tree
     * so we can append exactly one at the very end.
     */
    @Unique
    private MutableText stripIndicator(Text text) {
        String indicator = StorageConfig.INDICATOR;

        MutableText result;
        if (text.getContent() instanceof PlainTextContent.Literal lc) {
            String raw = lc.string();
            String stripped = raw.replace(indicator, "").replace(indicator.trim(), "");
            result = Text.literal(stripped).setStyle(text.getStyle());
        } else {
            result = text.copyContentOnly().setStyle(text.getStyle());
        }

        for (Text sibling : text.getSiblings()) {
            // Skip siblings that are exactly the indicator
            String sibStr = sibling.getString();
            if (sibStr.equals(indicator) || sibStr.equals(indicator.trim())) {
                continue;
            }
            result.append(stripIndicator(sibling));
        }

        return result;
    }

    @Unique
    private Text replaceNamesInTree(Text text) {
        // Never touch nodes whose Style carries a ClickEvent or HoverEvent –
        // rebuilding them would destroy links / tooltips the server attached.
        // Return the entire sub-tree unchanged (including siblings).
        if (hasInteractiveStyle(text)) {
            return text;
        }

        if (text.getContent() instanceof PlainTextContent.Literal lc) {
            String raw = lc.string();
            MutableText replaced = replaceConfiguredNamesOnePass(raw, text);
            if (replaced == null) {
                return rebuildWithNewSiblings(text);
            }

            for (Text sibling : text.getSiblings()) {
                replaced.append(replaceNamesInTree(sibling));
            }
            return replaced;
        }

        if (text.getContent() instanceof TranslatableTextContent tc) {

            Object[] args = tc.getArgs();
            if (args != null && args.length > 0) {
                Object[] newArgs = args.clone();
                boolean changed = false;

                for (int i = 0; i < newArgs.length; i++) {
                    Object a = newArgs[i];

                    if (a instanceof Text tArg) {
                        Text replacedArg = replacePlayerNameArgumentIfExact(tArg);
                        if (replacedArg != tArg) {
                            newArgs[i] = replacedArg;
                            changed = true;
                        } else {
                            Text deeper = replaceNamesInTree(tArg);
                            if (deeper != tArg) {
                                newArgs[i] = deeper;
                                changed = true;
                            }
                        }
                    } else if (a instanceof String sArg) {
                        Text asText = Text.literal(sArg);
                        Text replacedArg = replacePlayerNameArgumentIfExact(asText);
                        if (replacedArg != asText) {
                            newArgs[i] = replacedArg;
                            changed = true;
                        }
                    }
                }

                if (changed) {
                    MutableText rebuilt = Text.translatable(tc.getKey(), newArgs).setStyle(text.getStyle());
                    for (Text sibling : text.getSiblings()) {
                        rebuilt.append(replaceNamesInTree(sibling));
                    }
                    return rebuilt;
                }
            }
        }

        return rebuildWithNewSiblings(text);
    }

    @Unique
    private Text replacePlayerNameArgumentIfExact(Text tArg) {

        String full = tArg.getString();
        if (full == null || full.isBlank()) return tArg;

        // Strip any indicator that other mixins may have appended (e.g. " ✎")
        String cleaned = full.replace(StorageConfig.INDICATOR, "")
                             .replace(StorageConfig.INDICATOR.trim(), "")
                             .trim();
        if (cleaned.isEmpty()) return tArg;

        // Try 1: exact single-word match (simple case, e.g. just "Dasuro")
        String candidate = cleaned.replaceAll("[^A-Za-z0-9_]", "");
        if (!candidate.isEmpty() && !cleaned.contains(" ")) {
            NickEntry byOnline = resolveNickByOnlineName(candidate);
            if (byOnline != null) {
                Text base = Text.literal(candidate).setStyle(tArg.getStyle());
                // If the style has no color, try to get team color
                if (tArg.getStyle().getColor() == null) {
                    base = applyTeamColorIfAvailable(base, candidate);
                }
                return ColorParser.buildNick(byOnline, base);
            }
        }

        // Try 2: the argument contains team prefix/suffix + player name
        // (e.g. "Owner | Dasuro") – use the same one-pass replacement
        MutableText replaced = replaceConfiguredNamesOnePass(cleaned, tArg);
        if (replaced != null) {
            return replaced;
        }

        return tArg;
    }


    @Unique
    private NickEntry resolveNickByOnlineName(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getNetworkHandler() == null) return null;

        for (PlayerListEntry ple : mc.getNetworkHandler().getPlayerList()) {
            if (ple == null || ple.getProfile() == null) continue;
            String onlineName = ple.getProfile().name();
            if (onlineName != null && onlineName.equalsIgnoreCase(name)) {
                UUID uuid = ple.getProfile().id();
                if (uuid == null) return null;
                return NickConfig.get(uuid);
            }
        }
        return null;
    }

    /**
     * If the given text has no color in its style, looks up the player's team
     * color and applies it. Returns the text unchanged if no team color is found.
     */
    @Unique
    private Text applyTeamColorIfAvailable(Text text, String playerName) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getNetworkHandler() == null) return text;

        for (PlayerListEntry ple : mc.getNetworkHandler().getPlayerList()) {
            if (ple == null || ple.getProfile() == null) continue;
            String onlineName = ple.getProfile().name();
            if (onlineName != null && onlineName.equalsIgnoreCase(playerName)) {
                net.minecraft.scoreboard.Team team = ple.getScoreboardTeam();
                if (team != null && team.getColor().getColorValue() != null) {
                    return Text.literal(text.getString()).setStyle(
                            text.getStyle().withColor(TextColor.fromRgb(team.getColor().getColorValue()))
                    );
                }
                break;
            }
        }
        return text;
    }

    @Unique
    private MutableText replaceConfiguredNamesOnePass(String raw, Text styleSource) {
        if (raw == null || raw.isEmpty()) return null;

        Collection<NickEntry> entries = NickConfig.getAll().values();
        if (entries.isEmpty()) return null;

        List<NickEntry> candidates = new ArrayList<>(entries);
        candidates.sort(Comparator.comparingInt((NickEntry ne) -> ne.username == null ? 0 : ne.username.length()).reversed());

        List<String> names = new ArrayList<>();
        for (NickEntry ne : candidates) {
            if (ne == null || ne.username == null || ne.username.isBlank()) continue;
            names.add(Pattern.quote(ne.username));
        }
        if (names.isEmpty()) return null;

        Pattern p = Pattern.compile(
                "(?i)(?<!" + MC_NAME_CHARS + ")(" + String.join("|", names) + ")(?!" + MC_NAME_CHARS + ")"
        );

        Matcher m = p.matcher(raw);
        if (!m.find()) return null;

        m.reset();
        MutableText out = Text.empty();
        int last = 0;

        while (m.find()) {
            String before = raw.substring(last, m.start());
            if (!before.isEmpty()) {
                out.append(Text.literal(before).setStyle(styleSource.getStyle()));
            }

            String matchedName = m.group(1);

            NickEntry matched = null;
            for (NickEntry ne : candidates) {
                if (ne == null || ne.username == null) continue;
                if (ne.username.equalsIgnoreCase(matchedName)) {
                    matched = ne;
                    break;
                }
            }

            if (matched == null) {
                out.append(Text.literal(matchedName).setStyle(styleSource.getStyle()));
            } else {
                Text nameOriginal = Text.literal(matchedName).setStyle(styleSource.getStyle());
                // If the style has no color, try to get team color
                if (styleSource.getStyle().getColor() == null) {
                    nameOriginal = applyTeamColorIfAvailable(nameOriginal, matchedName);
                }
                out.append(ColorParser.buildNick(matched, nameOriginal));
            }

            last = m.end();
        }

        String tail = raw.substring(last);
        if (!tail.isEmpty()) {
            out.append(Text.literal(tail).setStyle(styleSource.getStyle()));
        }

        return out;
    }

    /**
     * Returns true if the node's own Style has a ClickEvent or HoverEvent.
     * We must not restructure such nodes because that would destroy the
     * interactive behaviour (links, tooltips, run-command, etc.).
     */
    @Unique
    private boolean hasInteractiveStyle(Text text) {
        net.minecraft.text.Style style = text.getStyle();
        if (style == null) return false;
        return style.getClickEvent() != null || style.getHoverEvent() != null;
    }

    @Unique
    private Text rebuildWithNewSiblings(Text text) {
        List<Text> siblings = text.getSiblings();
        if (siblings.isEmpty()) return text;

        boolean anyChanged = false;
        List<Text> newSiblings = new ArrayList<>(siblings.size());
        for (Text sibling : siblings) {
            Text processed = replaceNamesInTree(sibling);
            newSiblings.add(processed);
            if (processed != sibling) anyChanged = true;
        }

        // If nothing changed, return the original object to preserve identity
        // and avoid unnecessary reconstruction that could lose subtle state.
        if (!anyChanged) return text;

        MutableText rebuilt = text.copyContentOnly().setStyle(text.getStyle());
        for (Text s : newSiblings) {
            rebuilt.append(s);
        }
        return rebuilt;
    }
}
