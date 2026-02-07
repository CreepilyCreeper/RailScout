// Mixin into minecart tick to sample horizontal velocity and positions while the local player is riding.
// Uses sampling every 5 ticks. On dismount, writes survey JSON to disk.
package com.creepilycreeper.railscout.mixin;

import com.creepilycreeper.railscout.data.SurveySerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractMinecart.class)
public class MinecartEntityMixin {
    private static final double COPPER_THRESHOLD = 11.0; // m/s
    private static final int SAMPLE_TICKS = 5;

    private final List<SurveySerializer.Sample> railscout_samples = new ArrayList<>();
    private boolean railscout_wasRiding = false;
    private int railscout_tickCounter = 0;
    private static String activeDestCommand = null;
    private static final Logger LOGGER = LogManager.getLogger();

    public static void setActiveDestCommand(String cmd) {
        activeDestCommand = cmd;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void railscout_onTick(CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return;

            AbstractMinecart self = (AbstractMinecart) (Object) this;
            boolean playerRidingThis = mc.player.getVehicle() == self;
            railscout_tickCounter++;

            if (playerRidingThis) {
                railscout_wasRiding = true;
                if (railscout_tickCounter % SAMPLE_TICKS == 0) {
                    Vec3 motion = self.getDeltaMovement();
                    double horizSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z) * 20.0; // blocks/tick -> m/s
                    BlockPos pos = self.blockPosition();
                    long ts = System.currentTimeMillis();
                    railscout_samples.add(new SurveySerializer.Sample(ts, pos.getX(), pos.getY(), pos.getZ(), horizSpeed));
                }
            } else {
                if (railscout_wasRiding) {
                    try {
                        if (!railscout_samples.isEmpty()) {
                            String dim = mc.level != null ? mc.level.dimension().location().toString() : "unknown";
                            String uuid = mc.player.getUUID().toString();
                            String name = mc.player.getGameProfile().getName();
                            List<SurveySerializer.Segment> segments = SurveySerializer.computeSegmentsFromSamples(railscout_samples, COPPER_THRESHOLD);
                            SurveySerializer.writeSurveyFile(railscout_samples, segments, activeDestCommand, dim, uuid, name);
                        }
                    } catch (java.io.IOException e) {
                        LOGGER.error("Failed to write railscout survey file", e);
                    } catch (Exception e) {
                        LOGGER.error("Unexpected error while writing railscout survey", e);
                    } finally {
                        railscout_samples.clear();
                        railscout_wasRiding = false;
                        // reset counters/state
                        railscout_tickCounter = 0;
                        // activeDestCommand = null; // Maybe keep it for the next trip? Or clear it?
                    }
                }
            }
        } catch (Exception t) {
            LOGGER.error("Unhandled exception in railscout mixin tick", t);
        }
    }
}