# PowerShell script to apply RailScout mod files for Fabric 1.21.8 (Mojang mappings)
# Run from repository root: .\scripts\apply_railscout_patch.ps1
# This will overwrite target files with the RailScout implementation.

$files = @{
  "src/client/java/com/creepilycreeper/railscout/RailScoutClient.java" = @'
package com.creepilycreeper.railscout;

import com.creepilycreeper.railscout.mixin.MinecartEntityMixin;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.text.Text;

/**
 * Client entrypoint for RailScout.
 * Listens for /dest chat messages and forwards them to the mixin.
 */
public class RailScoutClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientReceiveMessageEvents.CHAT.register((message, type) -> {
            try {
                if (type == MessageType.SYSTEM || type == MessageType.CHAT) {
                    String raw = message.getString();
                    if (raw != null && raw.startsWith("/dest")) {
                        MinecartEntityMixin.setActiveDestCommand(raw.trim());
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            return true;
        });
    }
}
'@

  "src/client/java/com/creepilycreeper/railscout/mixin/MinecartEntityMixin.java" = @'
// Mixin into minecart tick to sample horizontal velocity and positions while the local player is riding.
// Uses sampling every 5 ticks. On dismount, writes survey JSON to disk.
package com.creepilycreeper.railscout.mixin;

import com.creepilycreeper.railscout.data.SurveySerializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
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

    private static final List<SurveySerializer.Sample> SAMPLES = new ArrayList<>();
    private static boolean wasRiding = false;
    private static double lastSpeed = 0.0;
    private static int tickCounter = 0;
    private static String activeDestCommand = null;

    public static void setActiveDestCommand(String cmd) {
        activeDestCommand = cmd;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void railscout_onTick(CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;

            AbstractMinecart self = (AbstractMinecart) (Object) this;
            boolean playerRidingThis = mc.player.getVehicle() == self;
            tickCounter++;

            if (playerRidingThis) {
                wasRiding = true;
                if (tickCounter % SAMPLE_TICKS == 0) {
                    Vec3 motion = self.getDeltaMovement();
                    double horizSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z) * 20.0; // blocks/tick -> m/s
                    BlockPos pos = new BlockPos(self.getX(), self.getY(), self.getZ());
                    long ts = System.currentTimeMillis();
                    SAMPLES.add(new SurveySerializer.Sample(ts, pos.getX(), pos.getY(), pos.getZ(), horizSpeed));

                    if (lastSpeed > COPPER_THRESHOLD && horizSpeed <= COPPER_THRESHOLD) {
                        // Segment break detected at pos; RLE will reflect this.
                    }
                    lastSpeed = horizSpeed;
                }
            } else {
                if (wasRiding) {
                    try {
                        List<SurveySerializer.Segment> segments = SurveySerializer.computeSegmentsFromSamples(SAMPLES, COPPER_THRESHOLD);
                        SurveySerializer.writeSurveyFile(SAMPLES, segments, activeDestCommand);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        SAMPLES.clear();
                        wasRiding = false;
                        lastSpeed = 0.0;
                        tickCounter = 0;
                        activeDestCommand = null;
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
'@

  "src/main/java/com/creepilycreeper/railscout/data/SurveySerializer.java" = @'
package com.creepilycreeper.railscout.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SurveySerializer {
    private SurveySerializer() {}

    public static class Sample {
        public final long timestamp;
        public final int x, y, z;
        public final double speed;

        public Sample(long timestamp, int x, int y, int z, double speed) {
            this.timestamp = timestamp;
            this.x = x;
            this.y = y;
            this.z = z;
            this.speed = speed;
        }
    }

    public static class Segment {
        public final int start_index;
        public final int end_index;
        public final String type;
        public final double avg_speed;

        public Segment(int start_index, int end_index, String type, double avg_speed) {
            this.start_index = start_index;
            this.end_index = end_index;
            this.type = type;
            this.avg_speed = avg_speed;
        }
    }

    public static class Survey {
        public final List<Sample> samples;
        public final List<Segment> segments;
        public final String dest_command;

        public Survey(List<Sample> samples, List<Segment> segments, String dest_command) {
            this.samples = samples;
            this.segments = segments;
            this.dest_command = dest_command;
        }
    }

    public static List<Segment> computeSegmentsFromSamples(List<Sample> samples, double threshold) {
        List<Segment> segments = new ArrayList<>();
        if (samples == null || samples.isEmpty()) return segments;

        int n = samples.size();
        int start = 0;
        boolean prevState = samples.get(0).speed > threshold;
        double sumSpeed = samples.get(0).speed;
        int count = 1;

        for (int i = 1; i < n; i++) {
            boolean state = samples.get(i).speed > threshold;
            if (state != prevState) {
                double avg = sumSpeed / count;
                segments.add(new Segment(start, i - 1, prevState ? "COPPERED" : "UNCOPPERED", avg));
                start = i;
                prevState = state;
                sumSpeed = samples.get(i).speed;
                count = 1;
            } else {
                sumSpeed += samples.get(i).speed;
                count++;
            }
        }
        double avg = sumSpeed / count;
        segments.add(new Segment(start, n - 1, prevState ? "COPPERED" : "UNCOPPERED", avg));
        return segments;
    }

    public static void writeSurveyFile(List<Sample> samples, List<Segment> segments, String destCommand) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Survey survey = new Survey(new ArrayList<>(samples), new ArrayList<>(segments), destCommand);

        File baseDir;
        try {
            baseDir = MinecraftClient.getInstance().runDirectory;
        } catch (Throwable t) {
            baseDir = new File(".");
        }
        File outDir = new File(baseDir, "railscout");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Failed to create railscout output directory at " + outDir.getAbsolutePath());
        }
        File outFile = new File(outDir, "survey_" + System.currentTimeMillis() + ".json");
        try (FileWriter fw = new FileWriter(outFile)) {
            gson.toJson(survey, fw);
        }
    }
}
'@

  "src/client/resources/railscout.client.mixins.json" = @'
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.creepilycreeper.railscout.mixin",
  "compatibilityLevel": "JAVA_17",
  "mixins": [
    "MinecartEntityMixin"
  ]
}
'@

  "src/main/resources/railscout.mixins.json" = @'
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.creepilycreeper.railscout.mixin",
  "compatibilityLevel": "JAVA_17",
  "mixins": [
    "MinecartEntityMixin"
  ]
}
'@

  "src/main/resources/fabric.mod.json" = @'
{
  "schemaVersion": 1,
  "id": "railscout",
  "version": "1.0.0",
  "name": "RailScout",
  "description": "Client-side rail telemetry for CivMC",
  "authors": ["CreepilyCreeper"],
  "contact": {
    "homepage": "https://github.com/CreepilyCreeper/RailScout"
  },
  "license": "MIT",
  "environment": "client",
  "entrypoints": {
    "client": [
      "com.creepilycreeper.railscout.RailScoutClient"
    ]
  },
  "depends": {
    "fabricloader": ">=0.16.0"
  },
  "mixins": [
    "railscout.mixins.json"
  ]
}
'@
}

foreach ($path in $files.Keys) {
    $dir = Split-Path $path
    if (!(Test-Path $dir)) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }
    $content = $files[$path]
    $content | Out-File -FilePath $path -Encoding UTF8
    Write-Host "Wrote $path"
}

Write-Host "Patch applied. Run './gradlew build' to compile the mod."