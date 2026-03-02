package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.config.StorageConfig;
import dev.dasuro.customnickname.util.ColorParser;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
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

        Text baseName = Text.literal(currentName != null ? currentName : "");
        Team team = self.getScoreboardTeam();
        if (team != null && team.getColor().getColorValue() != null) {
            baseName = Text.literal(currentName != null ? currentName : "").setStyle(
                    Style.EMPTY.withColor(TextColor.fromRgb(team.getColor().getColorValue()))
            );
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

