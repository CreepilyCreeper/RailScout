package com.creepilycreeper.railscout;

import com.creepilycreeper.railscout.mixin.MinecartEntityMixin;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

/**
 * Client entrypoint for RailScout.
 * Listens for outgoing /dest commands and forwards them to the mixin.
 */
public class RailScoutClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientSendMessageEvents.COMMAND.register((command) -> {
            if (command != null && command.startsWith("dest")) {
                MinecartEntityMixin.setActiveDestCommand("/" + command.trim());
            }
        });
    }
}