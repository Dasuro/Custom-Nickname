package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.config.StorageConfig;
import dev.dasuro.customnickname.util.ColorParser;
import dev.dasuro.customnickname.util.NickDisplayBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Unique
    private static final String MC_NAME_CHARS = "[A-Za-z0-9_]";


    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Component customnickname$onAddMessage(Component original) {
        return customnickname$transformIncomingMessage(original);
    }

    @Unique
    private Component customnickname$transformIncomingMessage(Component original) {
        MutableComponent result = replaceNamesInTree(original);
        boolean senderHasNick = isChatMessageFromNickedSender(original);


        // Show exactly one indicator at the very end when either
        // a name got replaced or the sender itself is nicked.
        if (StorageConfig.isShowIndicator() && (result != original || senderHasNick)) {
            // Strip leaked indicators only from the sender argument (chat.type.* arg0),
            // so user-written message content is not modified.
            MutableComponent cleaned = stripIndicatorFromChatSender(result);
            MutableComponent out = Component.empty();
            out.append(cleaned);
            out.append(Component.literal(StorageConfig.INDICATOR).withColor(0xFFFF00));
            return out;
        }

        return result;
    }

    @Unique
    private boolean isChatMessageFromNickedSender(Component text) {
        if (text == null) return false;
        if (text.getContents() instanceof TranslatableContents tc && tc.getKey().startsWith("chat.type.")) {
            Object[] args = tc.getArgs();
            if (args != null && args.length > 0) {
                return isNickedSenderArg(args[0]);
            }
        }
        for (Component sibling : text.getSiblings()) {
            if (isChatMessageFromNickedSender(sibling)) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private boolean isNickedSenderArg(Object arg) {
        if (arg instanceof Component tArg) {
            String full = tArg.getString();
            if (full != null && (full.contains(StorageConfig.INDICATOR.trim()) || full.contains(StorageConfig.SERVER_COLOR_MARKER.trim()))) {
                return true;
            }

            UUID senderUuid = extractEntityUuid(tArg);
            if (senderUuid != null) {
                return NickConfig.get(senderUuid) != null;
            }

            if (full == null || full.isBlank()) return false;

            String cleaned = stripKnownMarkers(full).trim();
            if (cleaned.isEmpty()) return false;

            String cleanedNoSection = SECTION_CODE_PATTERN.matcher(cleaned).replaceAll("");
            String candidate = cleanedNoSection.replaceAll("[^A-Za-z0-9_]", "");
            if (!candidate.isEmpty() && !cleanedNoSection.contains(" ")) {
                return resolveNickByOnlineName(candidate) != null;
            }

            return false;
        }

        if (arg instanceof String sArg) {
            if (sArg.contains(StorageConfig.INDICATOR.trim()) || sArg.contains(StorageConfig.SERVER_COLOR_MARKER.trim())) {
                return true;
            }

            String cleaned = stripKnownMarkers(sArg).trim();
            if (cleaned.isEmpty()) return false;

            String cleanedNoSection = SECTION_CODE_PATTERN.matcher(cleaned).replaceAll("");
            String candidate = cleanedNoSection.replaceAll("[^A-Za-z0-9_]", "");
            if (!candidate.isEmpty() && !cleanedNoSection.contains(" ")) {
                return resolveNickByOnlineName(candidate) != null;
            }
        }

        return false;
    }

    @Unique
    private MutableComponent stripIndicatorFromChatSender(Component text) {
        if (text == null) return Component.empty();

        if (text.getContents() instanceof TranslatableContents tc && tc.getKey().startsWith("chat.type.")) {
            Object[] args = tc.getArgs();
            if (args.length > 0) {
                Object[] newArgs = args.clone();
                Object senderArg = newArgs[0];

                if (senderArg instanceof Component senderText) {
                    newArgs[0] = stripIndicator(senderText);
                } else if (senderArg instanceof String senderStr) {
                    newArgs[0] = senderStr.replace(StorageConfig.INDICATOR, "")
                            .replace(StorageConfig.INDICATOR.trim(), "");
                }

                MutableComponent rebuilt = Component.translatable(tc.getKey(), newArgs).setStyle(text.getStyle());
                for (Component sibling : text.getSiblings()) {
                    rebuilt.append(stripIndicatorFromChatSender(sibling));
                }
                return rebuilt;
            }
        }

        // Non-chat nodes are kept untouched so message text stays exactly as sent.
        return asMutable(text);
    }

    /**
     * Recursively strips all occurrences of the indicator string from a Text tree
     * so we can append exactly one at the very end.
     */
    @Unique
    private MutableComponent stripIndicator(Component text) {
        String indicator = StorageConfig.INDICATOR;

        MutableComponent result;
        if (text.getContents() instanceof PlainTextContents plain) {
            String raw = plain.text();
            String stripped = raw.replace(indicator, "").replace(indicator.trim(), "");
            result = Component.literal(stripped).setStyle(text.getStyle());
        } else {
            result = text.plainCopy().setStyle(text.getStyle());
        }

        for (Component sibling : text.getSiblings()) {
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
    private MutableComponent replaceNamesInTree(Component text) {
        return replaceNamesInTree(text, Style.EMPTY);
    }


    @Unique
    private MutableComponent replaceNamesInTree(Component text, Style inheritedStyle) {
        // Resolve the effective style for this node: the node's own style
        // layered on top of what it inherits from its parent.  When we rebuild
        // fragments we must use this so that "inherited gray" is not lost and
        // replaced with white (Style.EMPTY).
        Style effectiveStyle = resolveStyle(inheritedStyle, text.getStyle());

        // For nodes with interactive styles (ClickEvent / HoverEvent), we still
        // want to replace names but must preserve the interactive events on the
        // result so that links / tooltips / run-command actions are not lost.
        boolean interactive = hasInteractiveStyle(text);

        if (text.getContents() instanceof PlainTextContents plain) {
            String raw = plain.text();
            MutableComponent replaced = replaceConfiguredNamesOnePass(raw, effectiveStyle);
            if (replaced == null) {
                // No name found in this node's own text.
                // For interactive nodes we still must process siblings –
                // on 1.8 servers the name might be in a child of this node.
                if (interactive) {
                    return rebuildWithNewSiblingsPreserveInteractive(text, effectiveStyle);
                }
                return rebuildWithNewSiblings(text, effectiveStyle);
            }

            // Wrap 'replaced' in a container that carries the original node's
            // style (and interactive events, if any).  This is critical because
            // 'replaced' is a Text.empty() with Style.EMPTY – any siblings that
            // inherit their style from the parent would lose the original color
            // (e.g. the dark-gray colon ": " or the message color) when
            // re-parented under the replacement node.  By preserving the
            // original style on the wrapper, sibling style inheritance works
            // exactly as before.
            Style originalNodeStyle = text.getStyle();
            boolean needsWrapper = interactive
                    || (originalNodeStyle != null && !originalNodeStyle.equals(Style.EMPTY));
            if (needsWrapper) {
                // Use the full original style (color, bold, click/hover, etc.)
                // so that both visual and interactive properties are preserved.
                Style wrapperStyle = originalNodeStyle != null ? originalNodeStyle : Style.EMPTY;
                if (interactive && (wrapperStyle.getClickEvent() == null && wrapperStyle.getHoverEvent() == null)) {
                    // Fallback: copy interactive events from the node style
                    Style src = text.getStyle();
                    if (src != null) {
                        wrapperStyle = wrapperStyle.withClickEvent(src.getClickEvent())
                                                   .withHoverEvent(src.getHoverEvent());
                    }
                }
                MutableComponent wrapper = Component.empty().setStyle(wrapperStyle);
                wrapper.append(replaced);
                replaced = wrapper;
            }

            for (Component sibling : text.getSiblings()) {
                replaced.append(replaceNamesInTree(sibling, effectiveStyle));
            }
            return replaced;
        }

        if (text.getContents() instanceof TranslatableContents tc) {

            Object[] args = tc.getArgs();
            if (args != null && args.length > 0) {
                Object[] newArgs = args.clone();
                boolean changed = false;

                // For chat messages (chat.type.text, chat.type.emote, etc.)
                // argument 0 is the player name – use exact matching.
                // argument 1+ is the message body – still replace names but
                // via the generic tree walk so that all occurrences are caught
                // (e.g. when a player mentions another player in chat).
                boolean isChatMessage = tc.getKey().startsWith("chat.type.");

                for (int i = 0; i < newArgs.length; i++) {
                    Object a = newArgs[i];

                    if (a instanceof Component tArg) {
                        if (isChatMessage && i == 0) {
                            // Sender argument: strict replacement only (UUID/exact).
                            // Do not run generic tree fallback here to avoid double remaps.
                            MutableComponent replacedArg = replacePlayerNameArgumentIfExact(tArg, effectiveStyle);
                            if (replacedArg != tArg) {
                                newArgs[i] = replacedArg;
                                changed = true;
                            }
                        } else {
                            // Message body or non-chat translatable: use tree walk
                            // so every occurrence of a configured name is replaced.
                            MutableComponent deeper = replaceNamesInTree(tArg, effectiveStyle);
                            if (deeper != tArg) {
                                newArgs[i] = deeper;
                                changed = true;
                            }
                        }
                    } else if (a instanceof String sArg) {
                        if (isChatMessage && i == 0) {
                            MutableComponent asText = Component.literal(sArg);
                            MutableComponent replacedArg = replacePlayerNameArgumentIfExact(asText, effectiveStyle);
                            if (replacedArg != asText) {
                                newArgs[i] = replacedArg;
                                changed = true;
                            }
                        } else {
                            // Message body as plain string: run one-pass replacement
                            MutableComponent replaced = replaceConfiguredNamesOnePass(sArg, effectiveStyle);
                            if (replaced != null) {
                                newArgs[i] = replaced;
                                changed = true;
                            }
                        }
                    }
                }

                if (changed) {
                    MutableComponent rebuilt = Component.translatable(tc.getKey(), newArgs).setStyle(text.getStyle());
                    for (Component sibling : text.getSiblings()) {
                        rebuilt.append(replaceNamesInTree(sibling, effectiveStyle));
                    }
                    return rebuilt;
                }
            }
        }

        if (interactive) {
            return rebuildWithNewSiblingsPreserveInteractive(text, effectiveStyle);
        }
        return rebuildWithNewSiblings(text, effectiveStyle);
    }


    /**
     * Resolves the effective style by layering the child's own style on top of
     * the inherited parent style.  The child's explicitly-set fields win;
     * anything not set on the child falls back to the parent (inherited) style.
     */
    @Unique
    private Style resolveStyle(Style parent, Style child) {
        if (child == null || child.equals(Style.EMPTY)) return parent;
        if (parent == null || parent.equals(Style.EMPTY)) return child;
        return child.applyTo(parent);
    }

    @Unique
    private MutableComponent replacePlayerNameArgumentIfExact(Component tArg, Style inheritedStyle) {

        String full = tArg.getString();
        if (full == null || full.isBlank()) return asMutable(tArg);

        // Prefer UUID-based sender resolution when available (stable against display-name rewrites).
        UUID senderUuid = extractEntityUuid(tArg);
        if (senderUuid != null) {
            NickEntry entry = NickConfig.get(senderUuid);
            if (entry != null) {
                Style effectiveStyle = resolveStyle(inheritedStyle, tArg.getStyle());
                String cleaned = full.replace(StorageConfig.INDICATOR, "")
                                     .replace(StorageConfig.INDICATOR.trim(), "")
                                     .replace(StorageConfig.SERVER_COLOR_MARKER, "")
                                     .replace(StorageConfig.SERVER_COLOR_MARKER.trim(), "")
                                     .trim();
                if (cleaned.isEmpty()) return asMutable(tArg);

                Style nameStyle = parseSectionCodesForStyle(cleaned, effectiveStyle);
                String nameText = SECTION_CODE_PATTERN.matcher(cleaned).replaceAll("");
                MutableComponent base = Component.literal(nameText).setStyle(nameStyle);
                MutableComponent replaced = ColorParser.buildNick(entry, base);
                NickDisplayBuilder.appendServerColorMarker(replaced, entry, base, null);
                return replaced;
            }
            // UUID found but no config entry: keep original argument untouched.
            return asMutable(tArg);
        }

        // Strip any indicator that other mixins may have appended (e.g. " ✎")
        String cleaned = stripKnownMarkers(full).trim();
        if (cleaned.isEmpty()) return asMutable(tArg);

        // Also strip §-formatting codes so names are recognised even when the
        // server sends "§aUsername§7: text" style messages.
        String cleanedNoSection = SECTION_CODE_PATTERN.matcher(cleaned).replaceAll("");

        // Resolve effective style for this argument
        Style effectiveStyle = resolveStyle(inheritedStyle, tArg.getStyle());

        // Try 0: If the argument has siblings (e.g. laby.net sends
        // empty[siblings=[literal{Name}[color=X], literal{: }[color=Y], literal{msg}[color=Z]]]),
        // use recursive tree replacement so each sibling keeps its own style.
        // This avoids flattening the text and losing per-sibling colors.
        if (!tArg.getSiblings().isEmpty()) {
            MutableComponent treeResult = replaceNamesInTree(tArg, inheritedStyle);
            if (treeResult != tArg) {
                return treeResult;
            }
        }

        // Try 1: exact single-word match (simple case, e.g. just "Dasuro")
        String candidate = cleanedNoSection.replaceAll("[^A-Za-z0-9_]", "");
        if (!candidate.isEmpty() && !cleanedNoSection.contains(" ")) {
            NickEntry byOnline = resolveNickByOnlineName(candidate);
            if (byOnline != null) {
                // Parse any §-codes in 'cleaned' to determine the style that
                // should apply to the name.  E.g. "§7PlayerName" → style = gray.
                Style nameStyle = parseSectionCodesForStyle(cleaned, effectiveStyle);
                MutableComponent base = Component.literal(candidate).setStyle(nameStyle);
                // Don't apply team color as fallback – the server's intended
                // styling (e.g. gray names) should be respected.
                MutableComponent replaced = ColorParser.buildNick(byOnline, base);
                NickDisplayBuilder.appendServerColorMarker(replaced, byOnline, base, null);
                return replaced;
            }
        }

        // For sender argument, avoid broad one-pass fallback to prevent
        // accidental remaps when display names are already modified elsewhere.
        return asMutable(tArg);
    }

    @Unique
    private MutableComponent asMutable(Component component) {
        return component instanceof MutableComponent mutable ? mutable : component.copy();
    }


    @Unique
    private NickEntry resolveNickByOnlineName(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null) return null;

        for (PlayerInfo ple : mc.getConnection().getOnlinePlayers()) {
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

    /** Matches a single Minecraft §-formatting code (§ + one character). */
    @Unique
    private static final Pattern SECTION_CODE_PATTERN = Pattern.compile("§[0-9a-fk-orA-FK-OR]");

    /**
     * Parses all §-codes in the given raw string and returns the style that
     * results from applying them sequentially on top of the given base style.
     * This is used to determine the correct color for a player name when the
     * server prepends §-codes (e.g. "§7PlayerName").
     */
    @Unique
    private Style parseSectionCodesForStyle(String raw, Style baseStyle) {
        if (raw == null || raw.indexOf('§') == -1) return baseStyle;
        Style current = baseStyle;
        for (int i = 0; i < raw.length() - 1; i++) {
            if (raw.charAt(i) == '§') {
                char code = Character.toLowerCase(raw.charAt(i + 1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')
                        || (code >= 'k' && code <= 'o') || code == 'r') {
                    current = applyLegacyCode(current, code);
                    i++; // skip the code character
                }
            }
        }
        return current;
    }

    @Unique
    private MutableComponent replaceConfiguredNamesOnePass(String raw, Style effectiveStyle) {
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

        // Strip §-codes for matching purposes, but keep a mapping from
        // stripped-index → original-index so we can slice the original string.
        String stripped = SECTION_CODE_PATTERN.matcher(raw).replaceAll("");
        Matcher m = p.matcher(stripped);
        if (!m.find()) return null;

        // Build index map: strippedPos → originalPos
        int[] toOriginal = buildStrippedToOriginalMap(raw);

        m.reset();
        MutableComponent out = Component.empty();
        // Tracks position in the *original* (un-stripped) string so that
        // §-codes that appear before the first visible character are not lost.
        int lastOriginal = 0;
        // Tracks the "active" style produced by §-codes seen so far in the
        // original string.  Starts from the inherited effective style so that
        // a parent's color is used until a §-code overrides it.
        Style sectionStyle = effectiveStyle;

        while (m.find()) {
            // Grab the "before" segment from the original string.
            // Use lastOriginal (not toOriginal[lastStripped]) so that leading
            // §-codes before the first visible character are included.
            int origStart = toOriginal[m.start()];
            if (lastOriginal < origStart) {
                String before = raw.substring(lastOriginal, origStart);
                if (!before.isEmpty()) {
                    sectionStyle = appendParsedSectionCodes(out, before, sectionStyle);
                }
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
                out.append(Component.literal(matchedName).setStyle(sectionStyle));
            } else {
                MutableComponent nameOriginal = Component.literal(matchedName).setStyle(sectionStyle);
                // Don't apply team color as fallback – the server's intended
                // styling (e.g. gray names) should be respected.
                MutableComponent replaced = ColorParser.buildNick(matched, nameOriginal);
                NickDisplayBuilder.appendServerColorMarker(replaced, matched, nameOriginal, null);
                out.append(replaced);
            }

            lastOriginal = toOriginal[m.end()];
        }

        // Tail
        if (lastOriginal < raw.length()) {
            String tail = raw.substring(lastOriginal);
            if (!tail.isEmpty()) {
                appendParsedSectionCodes(out, tail, sectionStyle);
            }
        }

        return out;
    }

    /**
     * Builds an array where index i (position in the stripped string) maps to the
     * corresponding position in the original string (which may contain §-codes).
     * The array length equals stripped.length() + 1 (the extra entry maps the
     * "end-of-stripped" position to its original counterpart).
     */
    @Unique
    private int[] buildStrippedToOriginalMap(String raw) {
        // First pass: determine stripped length
        String stripped = SECTION_CODE_PATTERN.matcher(raw).replaceAll("");
        int[] map = new int[stripped.length() + 1];

        int si = 0; // stripped index
        int oi = 0; // original index
        while (oi < raw.length()) {
            if (oi + 1 < raw.length() && raw.charAt(oi) == '§') {
                char code = Character.toLowerCase(raw.charAt(oi + 1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')
                        || (code >= 'k' && code <= 'o') || code == 'r') {
                    oi += 2; // skip the §-code
                    continue;
                }
            }
            map[si] = oi;
            si++;
            oi++;
        }
        // Map the "past-the-end" position
        map[si] = oi;
        return map;
    }

    @Unique
    private UUID extractEntityUuid(Component text) {
        HoverEvent hover = text.getStyle() != null ? text.getStyle().getHoverEvent() : null;
        if (hover instanceof HoverEvent.ShowEntity showEntity && hover.action() == HoverEvent.Action.SHOW_ENTITY) {
            HoverEvent.EntityTooltipInfo entity = showEntity.entity();
            if (entity != null && entity.uuid != null) {
                return entity.uuid;
            }
        }

        for (Component sibling : text.getSiblings()) {
            UUID found = extractEntityUuid(sibling);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Returns true if the node's own Style has a ClickEvent or HoverEvent.
     * We must not restructure such nodes because that would destroy the
     * interactive behaviour (links, tooltips, run-command, etc.).
     */
    @Unique
    private boolean hasInteractiveStyle(Component text) {
        net.minecraft.network.chat.Style style = text.getStyle();
        if (style == null) return false;
        return style.getClickEvent() != null || style.getHoverEvent() != null;
    }

    /**
     * Parses a raw string that may contain §-formatting codes and appends the
     * resulting styled text segments to {@code out}.  Returns the style that is
     * "active" at the end of the string (i.e. the last §-code's effect) so
     * that subsequent segments (like the matched player name) inherit the
     * correct color / formatting.
     *
     * @param out       the MutableText to append segments to
     * @param raw       the raw string potentially containing §-codes
     * @param baseStyle the style active before this segment
     * @return the style active after processing all §-codes in {@code raw}
     */
    @Unique
    private Style appendParsedSectionCodes(MutableComponent out, String raw, Style baseStyle) {
        if (raw == null || raw.isEmpty()) return baseStyle;

        // Fast path: no §-codes at all
        if (raw.indexOf('§') == -1) {
            out.append(Component.literal(raw).setStyle(baseStyle));
            return baseStyle;
        }

        Style current = baseStyle;
        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            if (i + 1 < raw.length() && raw.charAt(i) == '§') {
                char code = Character.toLowerCase(raw.charAt(i + 1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')
                        || (code >= 'k' && code <= 'o') || code == 'r') {
                    // Flush buffered text with the current style
                    if (!buf.isEmpty()) {
                        out.append(Component.literal(buf.toString()).setStyle(current));
                        buf.setLength(0);
                    }
                    current = applyLegacyCode(current, code);
                    i += 2;
                    continue;
                }
            }
            buf.append(raw.charAt(i));
            i++;
        }
        // Flush remaining text
        if (!buf.isEmpty()) {
            out.append(Component.literal(buf.toString()).setStyle(current));
        }
        return current;
    }

    /**
     * Applies a single legacy §-code character to the given style.
     * Handles color codes (0-9, a-f), formatting codes (k-o), and reset (r).
     */
    @Unique
    private Style applyLegacyCode(Style style, char code) {
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
            default  -> style;
        };
    }

    @Unique
    private String stripKnownMarkers(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replace(StorageConfig.INDICATOR, "")
                .replace(StorageConfig.INDICATOR.trim(), "")
                .replace(StorageConfig.SERVER_COLOR_MARKER, "")
                .replace(StorageConfig.SERVER_COLOR_MARKER.trim(), "");
    }

    @Unique
    private MutableComponent rebuildWithNewSiblings(Component text, Style inheritedStyle) {
        List<Component> siblings = text.getSiblings();
        if (siblings.isEmpty()) return asMutable(text);

        Style effectiveStyle = resolveStyle(inheritedStyle, text.getStyle());

        boolean anyChanged = false;
        List<MutableComponent> newSiblings = new ArrayList<>(siblings.size());
        for (Component sibling : siblings) {
            MutableComponent processed = replaceNamesInTree(sibling, effectiveStyle);
            newSiblings.add(processed);
            if (processed != sibling) anyChanged = true;
        }

        // If nothing changed, return the original object to preserve identity
        // and avoid unnecessary reconstruction that could lose subtle state.
        if (!anyChanged) return asMutable(text);

        MutableComponent rebuilt = text.plainCopy().setStyle(text.getStyle());
        for (MutableComponent s : newSiblings) {
            rebuilt.append(s);
        }
        return rebuilt;
    }

    /**
     * Like {@link #rebuildWithNewSiblings} but used for nodes that carry
     * interactive events (ClickEvent / HoverEvent).  Processes siblings
     * recursively and, if anything changed, rebuilds the node while
     * preserving the original interactive style so that click-to-reply,
     * hover-to-show-player-info, etc. are not lost.
     */
    @Unique
    private MutableComponent rebuildWithNewSiblingsPreserveInteractive(Component text, Style inheritedStyle) {
        List<Component> siblings = text.getSiblings();
        if (siblings.isEmpty()) return asMutable(text);

        Style effectiveStyle = resolveStyle(inheritedStyle, text.getStyle());

        boolean anyChanged = false;
        List<MutableComponent> newSiblings = new ArrayList<>(siblings.size());
        for (Component sibling : siblings) {
            MutableComponent processed = replaceNamesInTree(sibling, effectiveStyle);
            newSiblings.add(processed);
            if (processed != sibling) anyChanged = true;
        }

        if (!anyChanged) return asMutable(text);

        // Rebuild: keep the original content and style (including interactive events)
        MutableComponent rebuilt = text.plainCopy().setStyle(text.getStyle());
        for (MutableComponent s : newSiblings) {
            rebuilt.append(s);
        }
        return rebuilt;
    }
}
