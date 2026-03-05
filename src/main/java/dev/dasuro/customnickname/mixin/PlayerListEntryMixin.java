package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.config.StorageConfig;
import dev.dasuro.customnickname.util.ColorParser;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
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
        // Use Team.decorateName() as color reference – this gives us the team
        // color that Vanilla applies to the bare player name (matching the
        // EntityRendererMixin / nametag behavior). We must NOT use the server-
        // set displayName field because it contains the full prefix+name
        // formatting (e.g. "[light-blue number][dark-gray dash][name]") and
        // extractFirstStyle() would pick up the prefix color instead of the
        // name color.
        Text baseName;
        if (team != null) {
            baseName = Team.decorateName(team, Text.literal(currentName != null ? currentName : ""));
        } else {
            baseName = Text.literal(currentName != null ? currentName : "");
        }
        MutableText nickComponent = ColorParser.buildNick(nick, baseName);

        MutableText full = Text.empty();
        if (team != null && nick.showPrefix) full.append(team.getPrefix());
        full.append(nickComponent);
        if (team != null && nick.showSuffix) full.append(team.getSuffix());

        if (StorageConfig.isShowIndicator()) {
            full.append(Text.literal(StorageConfig.INDICATOR).styled(s -> s.withColor(0xFFFF00)));
        }

        cir.setReturnValue(full);
    }
}

