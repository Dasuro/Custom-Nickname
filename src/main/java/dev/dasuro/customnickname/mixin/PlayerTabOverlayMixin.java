package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.util.ColorParser;
import dev.dasuro.customnickname.util.NickDisplayBuilder;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void customnickname$onGetPlayerName(
            PlayerInfo entry,
            CallbackInfoReturnable<Component> cir
    ) {
        UUID uuid = entry.getProfile().id();
        String currentName = entry.getProfile().name();

        // Auto-update stored username if the player renamed
        NickConfig.updateUsernameIfChanged(uuid, currentName);

        NickEntry nick = NickConfig.get(uuid);
        if (nick == null) return;

        // If PlayerInfoMixin already replaced the display name,
        // getNameForDisplay() will already return that value.
        Component serverDisplayName = entry.getTabListDisplayName();
        if (serverDisplayName != null) {
            return;
        }

        // No server-set tab display name -> vanilla used Team-decorated profile name.
        PlayerTeam team = entry.getTeam();
        MutableComponent baseName = NickDisplayBuilder.buildStyledBaseName(currentName, null, team);
        MutableComponent nickComponent = ColorParser.buildNick(nick, baseName);

        cir.setReturnValue(NickDisplayBuilder.buildDisplay(nick, team, nickComponent, baseName));
    }
}
