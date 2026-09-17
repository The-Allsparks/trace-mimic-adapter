package org.allsparks.adapter.tracemimic.arch;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionSafetyTest {

    @Test
    void productionSourcesDoNotStartThreads() throws IOException {
        List<String> needles = Arrays.asList(
                "new Thread",
                "ExecutorService",
                "Executors.",
                "new Timer",
                "CompletableFuture",
                "ForkJoinPool",
                "ScheduledExecutor");
        List<String> hits = scan(needles);
        if (!hits.isEmpty()) {
            fail("production code must not start threads:\n" + String.join("\n", hits));
        }
    }

    @Test
    void productionSourcesDoNotCommandHardware() throws IOException {
        List<String> needles = Arrays.asList("setPower", "setVelocity", "setPosition", "DcMotor", "Servo", "CRServo");
        List<String> hits = scan(needles);
        if (!hits.isEmpty()) {
            fail("adapter must not command hardware:\n" + String.join("\n", hits));
        }
    }

    @Test
    void productionSourcesDoNotUseTraceStaticSingleton() throws IOException {
        List<String> hits = new ArrayList<>();
        Path main = SourceScan.mainJava();
        for (Path path : SourceScan.javaFiles(main)) {
            String[] lines = SourceScan.read(path).split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.trim().startsWith("*") || line.trim().startsWith("//")) {
                    continue;
                }
                if (line.contains("Trace.configure")
                        || line.contains("Trace.event")
                        || line.contains("Trace.record")
                        || line.contains("import org.allsparks.trace.Trace;")) {
                    hits.add(SourceScan.rel(main, path) + ":" + (i + 1));
                }
            }
        }
        if (!hits.isEmpty()) {
            fail("adapter must take TraceSession, not the Trace facade:\n" + String.join("\n", hits));
        }
    }

    private static List<String> scan(List<String> needles) throws IOException {
        List<String> hits = new ArrayList<>();
        Path main = SourceScan.mainJava();
        for (Path path : SourceScan.javaFiles(main)) {
            String[] lines = SourceScan.read(path).split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.trim().startsWith("*") || line.trim().startsWith("//")) {
                    continue;
                }
                for (String needle : needles) {
                    if (line.contains(needle)) {
                        hits.add(SourceScan.rel(main, path) + ":" + (i + 1) + " " + needle);
                    }
                }
            }
        }
        return hits;
    }
}
