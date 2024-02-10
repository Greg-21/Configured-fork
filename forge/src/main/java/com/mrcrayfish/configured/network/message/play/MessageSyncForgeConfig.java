package com.mrcrayfish.configured.network.message.play;

import com.mrcrayfish.configured.network.handler.ForgeClientPlayHandler;
import com.mrcrayfish.configured.network.handler.ForgeServerPlayHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.NetworkDirection;

/**
 * Author: MrCrayfish
 */
public record MessageSyncForgeConfig(String fileName, byte[] data)
{
    public static void encode(MessageSyncForgeConfig message, FriendlyByteBuf buffer)
    {
        buffer.writeUtf(message.fileName);
        buffer.writeByteArray(message.data);
    }

    public static MessageSyncForgeConfig decode(FriendlyByteBuf buffer)
    {
        return new MessageSyncForgeConfig(buffer.readUtf(), buffer.readByteArray());
    }

    public static void handle(MessageSyncForgeConfig message, CustomPayloadEvent.Context ctx)
    {
        if(ctx.getDirection() == NetworkDirection.PLAY_TO_SERVER)
        {
            ServerPlayer player = ctx.getSender();
            if(player != null)
            {
                ctx.enqueueWork(() -> ForgeServerPlayHandler.handleSyncServerConfigMessage(player, message));
            }
        }
        else
        {
            ctx.enqueueWork(() -> ForgeClientPlayHandler.handleSyncServerConfigMessage(ctx.getConnection(), message));
        }
        ctx.setPacketHandled(true);
    }
}
