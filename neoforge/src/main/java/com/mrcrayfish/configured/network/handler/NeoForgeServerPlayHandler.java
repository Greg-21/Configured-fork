package com.mrcrayfish.configured.network.handler;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.ConfigSpec;
import com.electronwill.nightconfig.core.concurrent.SynchronizedConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.google.common.base.Joiner;
import com.mrcrayfish.configured.Constants;
import com.mrcrayfish.configured.api.ActionResult;
import com.mrcrayfish.configured.impl.neoforge.NeoForgeConfig;
import com.mrcrayfish.configured.network.ServerPlayHelper;
import com.mrcrayfish.configured.network.payload.SyncNeoForgeConfigPayload;
import com.mrcrayfish.configured.util.ConfigHelper;
import com.mrcrayfish.configured.util.NeoForgeConfigHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.Logging;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Author: MrCrayfish
 */
public class NeoForgeServerPlayHandler
{
    public static void handleSyncServerConfigMessage(Player sender, SyncNeoForgeConfigPayload payload)
    {
        if(!(sender instanceof ServerPlayer player))
            return;

        Constants.LOG.debug("Received NeoForge server config sync from player: {}", sender.getName().getString());

        if(!ServerPlayHelper.canEditServerConfigs(player))
            return;

        ModConfig config = NeoForgeConfigHelper.getModConfig(payload.fileName());
        if(config == null)
        {
            Constants.LOG.warn("{} tried to update a NeoForge config that doesn't exist!", player.getName().getString());
            player.connection.disconnect(Component.translatable("configured.multiplayer.disconnect.bad_config_packet"));
            return;
        }

        if(config.getType() != ModConfig.Type.SERVER)
        {
            Constants.LOG.warn("{} tried to update a NeoForge config that isn't a server type", player.getName().getString());
            player.connection.disconnect(Component.translatable("configured.multiplayer.disconnect.bad_config_packet"));
            return;
        }

        try
        {
            if(config.getSpec() instanceof ModConfigSpec spec)
            {
                // Read the config data
                SynchronizedConfig updatedConfig = new SynchronizedConfig(TomlFormat.instance(), LinkedHashMap::new);
                updatedConfig.bulkCommentedUpdate(view -> {
                    TomlFormat.instance().createParser().parse(new ByteArrayInputStream(payload.data()), view, ParsingMode.REPLACE);
                });

                // Try and correct the config
                AtomicBoolean malformed = new AtomicBoolean();
                int result = spec.correct(updatedConfig, (action, path, incorrectValue, correctedValue) -> {
                    if(action == ConfigSpec.CorrectionAction.ADD || action == ConfigSpec.CorrectionAction.REMOVE) {
                        malformed.set(true);
                    } else if (action == ConfigSpec.CorrectionAction.REPLACE) {
                        Constants.LOG.warn("The value for path \"{}\" was originally \"{}\" but was corrected to \"{}\"", path, incorrectValue, correctedValue);
                    }
                }, (action, path, incorrectValue, correctedValue) -> {
                    // Ignore comment corrections
                });

                // If config data is missing or added new entries, reject update
                if(malformed.get())
                {
                    Constants.LOG.warn("{} send malformed config data when updating a NeoForge config: {}", player.getName().getString(), config.getFileName());
                    player.connection.disconnect(Component.translatable("configured.multiplayer.disconnect.bad_config_packet"));
                    return;
                }

                if(result != 0)
                {
                    Constants.LOG.info("Config data sent from {} needed to be corrected", player.getName().getString());
                }

                NeoForgeConfigHelper.setConfigData(config, updatedConfig);

                // Kick all players and ask them to rejoin
                player.server.getPlayerList().getPlayers().forEach(player1 -> {
                    player1.connection.disconnect(Component.translatable("configured.gui.neoforge.server_configs_updated"));
                });
            }
        }
        catch(ParsingException e)
        {
            Constants.LOG.warn("{} sent malformed config data to the server", player.getName().getString());
            player.connection.disconnect(Component.translatable("configured.multiplayer.disconnect.bad_config_packet"));
            ServerPlayHelper.sendMessageToOperators(Component.translatable("configured.chat.malformed_config_data", player.getName(), Component.literal(config.getFileName()).withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.RED), player);
            return;
        }
        catch(Exception e)
        {
            Constants.LOG.error("Failed to process config data sent by {}", player.getName().getString());
            player.connection.disconnect(Component.translatable("configured.multiplayer.disconnect.bad_config_packet"));
            ServerPlayHelper.sendMessageToOperators(Component.translatable("configured.chat.failed_config_update", Component.literal(config.getFileName()).withStyle(ChatFormatting.GRAY), player.getName()).withStyle(ChatFormatting.RED), player);
            return;
        }

        Constants.LOG.debug("Successfully processed config update for '" + payload.fileName() + "'");
        ServerPlayHelper.sendMessageToOperators(Component.translatable("configured.chat.config_updated", player.getName(), config.getFileName()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), player);
    }
}
