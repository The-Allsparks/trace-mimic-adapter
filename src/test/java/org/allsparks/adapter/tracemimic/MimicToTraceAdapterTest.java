package org.allsparks.adapter.tracemimic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.allsparks.contracts.health.HealthSeverity;
import org.allsparks.contracts.identity.ComponentId;
import org.allsparks.mimic.log.MimicEvent;
import org.allsparks.mimic.log.MimicEventType;
import org.allsparks.trace.TraceConfig;
import org.allsparks.trace.clock.ManualClock;
import org.allsparks.trace.core.RecordCategory;
import org.allsparks.trace.core.TraceRecord;
import org.allsparks.trace.core.TraceSeverity;
import org.allsparks.trace.session.TraceSession;
import org.junit.jupiter.api.Test;

class MimicToTraceAdapterTest {

    private final MimicToTraceAdapter adapter = new MimicToTraceAdapter();

    @Test
    void copiesTimestampTypeAndMessageOntoRecordingSession() {
        long timestamp = 1_234_567_890L;
        MimicEvent event = new MimicEvent(timestamp, MimicEventType.LOOP_SAMPLE, "observation", null);
        ManualClock clock = new ManualClock(timestamp, 0L);
        try (TraceSession session = memorySession(clock)) {
            adapter.record(event, session);
            TraceRecord record = onlyEvent(session);
            assertEquals(timestamp, record.monotonicNanos());
            assertEquals("MIMIC/LOOP_SAMPLE", record.name().value());
            assertEquals("1234567890 LOOP_SAMPLE observation", record.message());
            assertEquals(TraceSeverity.INFO, record.severity());
            assertEquals(RecordCategory.EVENT, record.category());
            assertEquals("MIMIC", record.source());
        }
    }

    @Test
    void faultUsesTraceMappingsStopComponentSeverity() {
        MimicEvent event = new MimicEvent(10L, MimicEventType.FAULT, "stall", null);
        assertEquals(TraceSeverity.FAULT, MimicToTraceAdapter.traceSeverity(event.type()));
        assertEquals(HealthSeverity.STOP_COMPONENT, MimicToTraceAdapter.healthSeverity(event.type()));
        try (TraceSession session = memorySession(new ManualClock(10L, 0L))) {
            adapter.record(event, session);
            assertEquals(TraceSeverity.FAULT, onlyEvent(session).severity());
        }
    }

    @Test
    void sensorInvalidUsesTraceMappingsDegradedSeverity() {
        MimicEvent event = new MimicEvent(11L, MimicEventType.SENSOR_INVALID, "observation", null);
        assertEquals(TraceSeverity.ERROR, MimicToTraceAdapter.traceSeverity(event.type()));
        assertEquals(HealthSeverity.DEGRADED, MimicToTraceAdapter.healthSeverity(event.type()));
        try (TraceSession session = memorySession(new ManualClock(11L, 0L))) {
            adapter.record(event, session);
            assertEquals(TraceSeverity.ERROR, onlyEvent(session).severity());
        }
    }

    @Test
    void mechanismIdFieldBecomesComponentIdInSignalName() {
        Map<String, String> fields = new HashMap<>();
        fields.put("id", "mainLift");
        MimicEvent event = new MimicEvent(20L, MimicEventType.GOAL_ACCEPTED, "up", fields);
        assertEquals(
                ComponentId.of("mainLift"),
                MimicToTraceAdapter.componentId(event).get());
        try (TraceSession session = memorySession(new ManualClock(20L, 0L))) {
            adapter.record(event, session);
            TraceRecord record = onlyEvent(session);
            assertEquals("MIMIC/mainLift/GOAL_ACCEPTED", record.name().value());
            assertEquals("MIMIC", record.source());
        }
    }

    @Test
    void mechanismIdKeyIsAcceptedWhenIdIsAbsent() {
        Map<String, String> fields = new HashMap<>();
        fields.put("mechanismId", "intake-left");
        MimicEvent event = new MimicEvent(21L, MimicEventType.LIMIT_ASSERTED, "lower", fields);
        assertEquals("intake-left", MimicToTraceAdapter.componentId(event).get().value());
        try (TraceSession session = memorySession(new ManualClock(21L, 0L))) {
            adapter.record(event, session);
            assertEquals(
                    "MIMIC/intake_left/LIMIT_ASSERTED",
                    onlyEvent(session).name().value());
        }
    }

    @Test
    void otherEventTypesStayInfo() {
        for (MimicEventType type : MimicEventType.values()) {
            if (type == MimicEventType.FAULT || type == MimicEventType.SENSOR_INVALID) {
                continue;
            }
            assertEquals(TraceSeverity.INFO, MimicToTraceAdapter.traceSeverity(type), type.name());
            assertEquals(HealthSeverity.INFO, MimicToTraceAdapter.healthSeverity(type), type.name());
        }
    }

    @Test
    void nullInputsAreRejected() {
        MimicEvent event = new MimicEvent(1L, MimicEventType.STOP_REQUESTED, "halt", Collections.emptyMap());
        try (TraceSession session = memorySession(new ManualClock(1L, 0L))) {
            assertThrows(NullPointerException.class, () -> adapter.record(null, session));
            assertThrows(NullPointerException.class, () -> adapter.record(event, null));
        }
    }

    @Test
    void blankMechanismIdIsOmittedFromSignalName() {
        Map<String, String> fields = new HashMap<>();
        fields.put("id", "  ");
        MimicEvent event = new MimicEvent(22L, MimicEventType.LOOP_SAMPLE, "observation", fields);
        assertEquals(false, MimicToTraceAdapter.componentId(event).isPresent());
        try (TraceSession session = memorySession(new ManualClock(22L, 0L))) {
            adapter.record(event, session);
            assertEquals("MIMIC/LOOP_SAMPLE", onlyEvent(session).name().value());
        }
    }

    @Test
    void onEventUsesConstructedSessionWhenIntegrationEnabled() {
        MimicEvent event = new MimicEvent(30L, MimicEventType.LOOP_SAMPLE, "observation", null);
        try (TraceSession session = new TraceSession(TraceConfig.builder()
                .memorySink(true)
                .fileSink(false)
                .consoleSink(false)
                .enableIntegration("MIMIC")
                .build())) {
            MimicToTraceAdapter wired = new MimicToTraceAdapter(session);
            wired.onEvent(event);
            assertEquals("MIMIC/LOOP_SAMPLE", onlyEvent(session).name().value());
        }
    }

    private static TraceSession memorySession(ManualClock clock) {
        return new TraceSession(TraceConfig.builder()
                .clock(clock)
                .memorySink(true)
                .fileSink(false)
                .consoleSink(false)
                .captureWallClock(false)
                .build());
    }

    private static TraceRecord onlyEvent(TraceSession session) {
        List<TraceRecord> recorded = session.recorded();
        assertEquals(1, recorded.size(), recorded.toString());
        TraceRecord record = recorded.get(0);
        assertTrue(record.name().value().startsWith("MIMIC/"));
        return record;
    }
}
