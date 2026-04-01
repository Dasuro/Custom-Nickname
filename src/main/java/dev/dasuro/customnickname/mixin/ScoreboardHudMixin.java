package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.util.NickDisplayBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Comparator;
import java.util.UUID;

@Mixin(PlayerTeam.class)
public abstract class ScoreboardHudMixin {

    @Inject(method = "formatNameForTeam", at = @At("RETURN"), cancellable = true)
    private static void customnickname$replaceTeamFormattedName(
            CallbackInfoReturnable<Component> cir
    ) {
        Component originalReturn = cir.getReturnValue();
        MutableComponent original = originalReturn == null ? null : originalReturn.copy();
        if (original == null) return;

        String raw = original.getString();
        if (raw.isBlank()) return;

        PlayerInfo matched = customnickname$findBestPlayerMatch(raw);
        if (matched == null) return;

        UUID uuid = matched.getProfile().id();
        String currentName = matched.getProfile().name();
        if (uuid == null || currentName == null || currentName.isBlank()) return;

        NickConfig.updateUsernameIfChanged(uuid, currentName);

        NickEntry nick = NickConfig.get(uuid);
        if (nick == null) return;

        PlayerTeam playerTeam = matched.getTeam();
        MutableComponent replaced = NickDisplayBuilder.replaceInOriginalOrFallback(
                original.copy(),
                currentName,
                nick,
                playerTeam,
                true,
                true
        );

        cir.setReturnValue(replaced);
    }

    @Unique
    private static PlayerInfo customnickname$findBestPlayerMatch(String rawText) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) return null;

        Collection<PlayerInfo> onlinePlayers = client.getConnection().getOnlinePlayers();
        return onlinePlayers.stream()
                .filter(info -> info != null)
                .filter(info -> {
                    String name = info.getProfile().name();
                    return name != null
                            && !name.isBlank()
                            && customnickname$containsStandaloneName(rawText, name);
                })
                .max(Comparator.comparingInt(info -> info.getProfile().name().length()))
                .orElse(null);
    }

    @Unique
    private static boolean customnickname$containsStandaloneName(String raw, String name) {
        int index = raw.indexOf(name);
        while (index >= 0) {
            int end = index + name.length();
            boolean leftOk = index == 0 || !customnickname$isNameChar(raw.charAt(index - 1));
            boolean rightOk = end >= raw.length() || !customnickname$isNameChar(raw.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            index = raw.indexOf(name, index + 1);
        }
        return false;
    }

    @Unique
    private static boolean customnickname$isNameChar(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '_';
    }
}
