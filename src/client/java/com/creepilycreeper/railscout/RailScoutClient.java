package com.creepilycreeper.railscout;

import com.creepilycreeper.railscout.mixin.MinecartEntityMixin;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.ChatType;
import com.mojang.authlib.GameProfile;

import java.time.Instant;

/**
 * Client entrypoint for RailScout.
 * Listens for /dest chat messages and forwards them to the mixin.
 */
public class RailScoutClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientReceiveMessageEvents.CHAT.register((Component message, PlayerChatMessage signedMessage, GameProfile senderProfile, ChatType.Bound chatType, Instant time) -> {
            try {
                String raw = message.getString();
                if (raw != null && raw.startsWith("/dest")) {
                    MinecartEntityMixin.setActiveDestCommand(raw.trim());
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }
}