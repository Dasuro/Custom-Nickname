package dev.dasuro.customnickname.mixin;

import dev.dasuro.customnickname.config.NickConfig;
import dev.dasuro.customnickname.config.NickEntry;
import dev.dasuro.customnickname.config.StorageConfig;
import dev.dasuro.customnickname.util.ColorParser;
import net.minecraft.entity.player.PlayerEntity;
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
 * Hooks into PlayerEntity.getDisplayName() and PlayerEntity.getName() so that
 * ANY code path (including Entity Culling's separate nametag rendering for
 * culled entities) receives the custom nickname instead of the vanilla name.
 * <p>
 * This is necessary because Entity Culling bypasses the normal
 * PlayerEntityRenderer.updateRenderState() pipeline when an entity is culled
 * (behind a wall) and directly calls entity.getDisplayName() / getName()
 * to render the nametag, which would otherwise show the original vanilla name.
 */
@Mixin(PlayerEntity.class)
public class PlayerEntityDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void customnickname$onGetDisplayName(CallbackInfoReturnable<Text> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        UUID uuid = self.getUuid();
        String currentName = self.getGameProfile().name();

        NickConfig.updateUsernameIfChanged(uuid, currentName);

        NickEntry nick = NickConfig.get(uuid);
        if (nick == null) return;

        Text baseName = Text.literal(currentName);
        Team team = self.getScoreboardTeam();
        if (team != null && team.getColor().getColorValue() != null) {
            baseName = Text.literal(currentName).setStyle(
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

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void customnickname$onGetName(CallbackInfoReturnable<Text> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        UUID uuid = self.getUuid();
        String currentName = self.getGameProfile().name();

        NickConfig.updateUsernameIfChanged(uuid, currentName);

        NickEntry nick = NickConfig.get(uuid);
        if (nick == null) return;

        Text baseName = Text.literal(currentName);
        Team teamN = self.getScoreboardTeam();
        if (teamN != null && teamN.getColor().getColorValue() != null) {
            baseName = Text.literal(currentName).setStyle(
                    Style.EMPTY.withColor(TextColor.fromRgb(teamN.getColor().getColorValue()))
            );
        }
        MutableText nickComponent = ColorParser.buildNick(nick, baseName);

        // getName() should return just the name without team decorations,
        // as getDisplayName() adds team prefix/suffix on top of getName()
        cir.setReturnValue(nickComponent);
    }
}


