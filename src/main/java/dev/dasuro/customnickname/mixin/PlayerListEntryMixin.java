package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.util.NickDisplayBuilder;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Mixin on PlayerListEntry itself – this is called by any tab-list implementation
 * (Vanilla, LabyMod, etc.) to retrieve the display name for a player.
 * This ensures our nickname replacement works even when a modded client
 * replaces the PlayerListHud rendering.
 */
@Mixin(PlayerListEntry.class)
public class PlayerListEntryMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void customnickname$onGetDisplayName(CallbackInfoReturnable<Text> cir) {
        PlayerListEntry self = (PlayerListEntry) (Object) this;
        if (self.getProfile() == null) return;

        UUID uuid = self.getProfile().id();
        if (uuid == null) return;

        String currentName = self.getProfile().name();

        // Auto-update stored username if the player renamed
        NickConfig.updateUsernameIfChanged(uuid, currentName);

        NickEntry nick = NickConfig.get(uuid);
        if (nick == null) return;

        Team team = self.getScoreboardTeam();
        cir.setReturnValue(NickDisplayBuilder.replaceInOriginalOrFallback(cir.getReturnValue(), currentName, nick, team, true, true));
    }
}
