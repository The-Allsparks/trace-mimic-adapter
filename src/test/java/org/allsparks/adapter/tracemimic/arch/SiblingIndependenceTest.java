package org.allsparks.adapter.tracemimic.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the adapter's reason to exist: TRACE and MIMIC still compile without
 * each other. The adapter is the only compile-time edge between them.
 */
class SiblingIndependenceTest {

    @Test
    void traceDoesNotDependOnMimic() {
        Path traceRoot = SourceScan.sibling("TRACE");
        if (!Files.isRegularFile(traceRoot.resolve("settings.gradle"))) {
            return;
        }
        List<String> hits = new ArrayList<>();
        scanGradle(traceRoot.resolve("build.gradle"), "mimic", hits);
        scanGradle(traceRoot.resolve("settings.gradle"), "mimic", hits);
        Path advantagescope = traceRoot.resolve("trace-advantagescope/build.gradle");
        if (Files.isRegularFile(advantagescope)) {
            scanGradle(advantagescope, "mimic", hits);
        }
        if (!hits.isEmpty()) {
            fail("TRACE must not depend on MIMIC:\n" + String.join("\n", hits));
        }
    }

    @Test
    void mimicDoesNotDependOnTrace() {
        Path mimicRoot = SourceScan.sibling("MIMIC");
        if (!Files.isRegularFile(mimicRoot.resolve("settings.gradle"))) {
            return;
        }
        List<String> hits = new ArrayList<>();
        scanGradle(mimicRoot.resolve("build.gradle"), "trace", hits);
        scanGradle(mimicRoot.resolve("settings.gradle"), "trace", hits);
        if (!hits.isEmpty()) {
            fail("MIMIC must not depend on TRACE:\n" + String.join("\n", hits));
        }
    }

    @Test
    void adapterDeclaresBothLibraries() {
        String gradle = SourceScan.read(SourceScan.buildGradle());
        assertTrue(gradle.contains("org.allsparks:trace:"), gradle);
        assertTrue(gradle.contains("org.allsparks:mimic:"), gradle);
        assertTrue(gradle.contains("org.allsparks:allsparks-contracts:"), gradle);
    }

    private static void scanGradle(Path file, String needle, List<String> hits) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        String[] lines = SourceScan.read(file).split("\n");
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                continue;
            }
            String lower = trimmed.toLowerCase();
            boolean module = lower.contains("org.allsparks:" + needle);
            boolean included = lower.contains("includebuild") && lower.contains(needle);
            if (module || included) {
                hits.add(file.toString().replace('\\', '/') + ":" + (i + 1) + " " + trimmed);
            }
        }
    }
}
