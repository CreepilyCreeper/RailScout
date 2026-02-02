package com.creepilycreeper.railscout.data;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class SurveySerializerTest {

    @Test
    public void testComputeSegmentsEmpty() {
        List<SurveySerializer.Segment> segments = SurveySerializer.computeSegmentsFromSamples(new ArrayList<>(), 11.0);
        assertTrue(segments.isEmpty());
    }

    @Test
    public void testComputeSegmentsSingleSample() {
        List<SurveySerializer.Sample> samples = List.of(
            new SurveySerializer.Sample(1000, 0, 64, 0, 15.0)
        );
        List<SurveySerializer.Segment> segments = SurveySerializer.computeSegmentsFromSamples(samples, 11.0);
        assertEquals(1, segments.size());
        assertEquals("COPPERED", segments.get(0).type);
        assertEquals(0, segments.get(0).start_index);
        assertEquals(0, segments.get(0).end_index);
        assertEquals(15.0, segments.get(0).avg_speed);
    }

    @Test
    public void testComputeSegmentsTransition() {
        List<SurveySerializer.Sample> samples = List.of(
            new SurveySerializer.Sample(1000, 0, 64, 0, 15.0),
            new SurveySerializer.Sample(1005, 1, 64, 0, 15.0),
            new SurveySerializer.Sample(1010, 2, 64, 0, 5.0),
            new SurveySerializer.Sample(1015, 3, 64, 0, 5.0)
        );
        List<SurveySerializer.Segment> segments = SurveySerializer.computeSegmentsFromSamples(samples, 11.0);
        assertEquals(2, segments.size());
        
        assertEquals("COPPERED", segments.get(0).type);
        assertEquals(0, segments.get(0).start_index);
        assertEquals(1, segments.get(0).end_index);
        assertEquals(15.0, segments.get(0).avg_speed);

        assertEquals("UNCOPPERED", segments.get(1).type);
        assertEquals(2, segments.get(1).start_index);
        assertEquals(3, segments.get(1).end_index);
        assertEquals(5.0, segments.get(1).avg_speed);
    }

    @Test
    public void testComputeSegmentsRapidToggles() {
        List<SurveySerializer.Sample> samples = List.of(
            new SurveySerializer.Sample(1000, 0, 64, 0, 15.0),
            new SurveySerializer.Sample(1005, 1, 64, 0, 5.0),
            new SurveySerializer.Sample(1010, 2, 64, 0, 15.0)
        );
        List<SurveySerializer.Segment> segments = SurveySerializer.computeSegmentsFromSamples(samples, 11.0);
        assertEquals(3, segments.size());
        assertEquals("COPPERED", segments.get(0).type);
        assertEquals("UNCOPPERED", segments.get(1).type);
        assertEquals("COPPERED", segments.get(2).type);
    }
}
