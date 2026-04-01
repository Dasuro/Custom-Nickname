package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.util.ColorParser;
import dev.dasuro.customnickname.util.NickDisplayBuilder;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void customnickname$onGetPlayerName(
            PlayerListEntry entry,
            CallbackInfoReturnable<Text> cir
    ) {
        UUID uuid = entry.getProfile().id();
        String currentName = entry.getProfile().name();

        // Auto-update stored username if the player renamed
        NickConfig.updateUsernameIfChanged(uuid, currentName);

        NickEntry nick = NickConfig.get(uuid);
        if (nick == null) return;

        // If PlayerListEntryMixin already replaced the display name,
        // getPlayerName() will have returned that replacement as its result.
        // In that case we skip to avoid double-processing.
        // We detect this by checking if entry.getDisplayName() is non-null –
        // if it is, PlayerListEntryMixin already handled it (since it hooks
        // getDisplayName() and sets the return value).
        // If entry.getDisplayName() was originally null, Vanilla's getPlayerName()
        // builds the name from Team.decorateName() – then we need to handle it here.
        Text serverDisplayName = entry.getDisplayName();
        if (serverDisplayName != null) {
            // PlayerListEntryMixin already handled this – getPlayerName() returned
            // the result from getDisplayName() which is our modified nick.
            // Don't process again.
            return;
        }

        // No server-set display name → Vanilla used Team.decorateName().
        // Build our own replacement using team color as base.
        Team team = entry.getScoreboardTeam();
        Text baseName = NickDisplayBuilder.buildStyledBaseName(currentName, null, team);
        MutableText nickComponent = ColorParser.buildNick(nick, baseName);

        cir.setReturnValue(NickDisplayBuilder.buildDisplay(nick, team, nickComponent));
    }
}
