package com.creepilycreeper.railscout.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

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
            baseDir = FabricLoader.getInstance().getGameDir().toFile();
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