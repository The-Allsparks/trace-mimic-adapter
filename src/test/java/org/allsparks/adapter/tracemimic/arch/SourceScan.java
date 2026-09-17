package org.allsparks.adapter.tracemimic.arch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Locates Gradle source trees from unit-test working directories. */
final class SourceScan {
    private SourceScan() {}

    static Path repoRoot() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path cur = cwd;
        for (int i = 0; i < 8 && cur != null; i++) {
            if (Files.isRegularFile(cur.resolve("settings.gradle"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        return cwd;
    }

    static Path mainJava() {
        return repoRoot().resolve("src/main/java");
    }

    static Path buildGradle() {
        return repoRoot().resolve("build.gradle");
    }

    static Path sibling(String directory) {
        return repoRoot().getParent().resolve(directory);
    }

    static List<Path> javaFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return files;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.forEach(path -> {
                if (path.toString().endsWith(".java") && Files.isRegularFile(path)) {
                    files.add(path);
                }
            });
        }
        return files;
    }

    static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    static String rel(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }
}
