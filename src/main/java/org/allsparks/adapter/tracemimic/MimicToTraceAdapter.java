package org.allsparks.adapter.tracemimic;

import java.util.Objects;
import java.util.Optional;
import org.allsparks.contracts.health.HealthSeverity;
import org.allsparks.contracts.identity.ComponentId;
import org.allsparks.mimic.log.MimicEvent;
import org.allsparks.mimic.log.MimicEventSink;
import org.allsparks.mimic.log.MimicEventType;
import org.allsparks.trace.contracts.TraceMappings;
import org.allsparks.trace.core.TracePriority;
import org.allsparks.trace.core.TraceSeverity;
import org.allsparks.trace.session.TraceSession;

/**
 * Copies a MIMIC event onto a TRACE session without commanding hardware.
 *
 * <p>TRACE and MIMIC remain independently adoptable. This adapter is the
 * compile-time edge between them. {@link #onEvent} records onto the
 * {@link TraceSession} supplied at construction. {@link #record} still
 * accepts an explicit session for tests.
 */
public final class MimicToTraceAdapter implements MimicEventSink {

    static final String SIGNAL_ROOT = "MIMIC";

    private final TraceSession sinkSession;

    public MimicToTraceAdapter() {
        this(null);
    }

    public MimicToTraceAdapter(TraceSession sinkSession) {
        this.sinkSession = sinkSession;
    }

    /**
     * MIMIC calls this from {@code MimicSession} when wired as {@code eventSink}.
     * Fail-open so a TRACE typo cannot freeze the mechanism observe loop.
     */
    @Override
    public void onEvent(MimicEvent event) {
        if (event == null || sinkSession == null) {
            return;
        }
        try {
            if (!sinkSession.integrationEnabled("MIMIC")) {
                return;
            }
            record(event, sinkSession);
        } catch (RuntimeException | Error ex) {
            try {
                sinkSession.recordException(ex);
            } catch (RuntimeException | Error ignored) {
                // Recording the failure also failed. The mechanism loop continues.
            }
        }
    }

    /**
     * Record one MIMIC event onto {@code session}.
     *
     * <p>{@link MimicEvent#timestampNanos()}, {@link MimicEvent#type()}, and
     * {@link MimicEvent#message()} are copied into {@link TraceSession#event}.
     * A mechanism id field becomes a {@link ComponentId} in the signal name
     * when present. {@link MimicEventType#FAULT} and {@link
     * MimicEventType#SENSOR_INVALID} use {@link TraceMappings} for severity;
     * other types record as {@link TraceSeverity#INFO}.
     *
     * @param event MIMIC event to copy; must not be {@code null}
     * @param session TRACE session that receives the event; must not be {@code null}
     */
    public void record(MimicEvent event, TraceSession session) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(session, "session");
        TraceSeverity severity = traceSeverity(event.type());
        // Invoke TRACE's published mapping so FAULT / ERROR keep that meaning.
        TraceMappings.toHealthSeverity(severity);
        session.event(signalName(event), payload(event), severity, TracePriority.HIGH);
    }

    static TraceSeverity traceSeverity(MimicEventType type) {
        Objects.requireNonNull(type, "type");
        if (type == MimicEventType.FAULT) {
            return TraceSeverity.FAULT;
        }
        if (type == MimicEventType.SENSOR_INVALID) {
            return TraceSeverity.ERROR;
        }
        return TraceSeverity.INFO;
    }

    static HealthSeverity healthSeverity(MimicEventType type) {
        return TraceMappings.toHealthSeverity(traceSeverity(type));
    }

    static String signalName(MimicEvent event) {
        Optional<String> component = componentSegment(event);
        String type = event.type().name();
        if (component.isPresent()) {
            return SIGNAL_ROOT + "/" + component.get() + "/" + type;
        }
        return SIGNAL_ROOT + "/" + type;
    }

    static String payload(MimicEvent event) {
        return event.timestampNanos() + " " + event.type().name() + " " + event.message();
    }

    static Optional<ComponentId> componentId(MimicEvent event) {
        String raw = firstNonBlank(event.fields().get("id"), event.fields().get("mechanismId"));
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(ComponentId.of(raw));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Optional<String> componentSegment(MimicEvent event) {
        Optional<ComponentId> id = componentId(event);
        if (!id.isPresent()) {
            return Optional.empty();
        }
        String segment = sanitizeSegment(id.get().value());
        if (segment.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(segment);
    }

    /**
     * TRACE {@code SignalName} segments are {@code [A-Za-z][A-Za-z0-9_]*}.
     * Mechanism ids may contain hyphens; those become underscores.
     */
    static String sanitizeSegment(String raw) {
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') {
                builder.append(ch);
            } else {
                builder.append('_');
            }
        }
        if (builder.length() == 0) {
            return "";
        }
        if (!isLetter(builder.charAt(0))) {
            builder.insert(0, 'M');
        }
        return builder.toString();
    }

    private static boolean isLetter(char ch) {
        return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        if (second != null && !second.trim().isEmpty()) {
            return second;
        }
        return null;
    }
}
