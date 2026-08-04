package com.basaltrock.testcontainers;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Stream;

/**
 * Testcontainer that manages a temporary Docker context directory.
 * The temp directory is automatically cleaned up when the container stops.
 */
public class TempComposeBasaltrockContainer extends ComposeBasaltrockContainer {

    static final Logger logger = LoggerFactory.getLogger(TempComposeBasaltrockContainer.class);
    final File tempDir;

    public TempComposeBasaltrockContainer(String dataResourcePath) throws IOException, URISyntaxException {
        this(Files.createTempDirectory("basaltrock-").toFile(), dataResourcePath);
    }

    TempComposeBasaltrockContainer(File tempDir, String dataResourcePath) throws IOException, URISyntaxException {
        super(prepareContext(tempDir, dataResourcePath), new File(tempDir, "data").getAbsolutePath());
        this.tempDir = tempDir;
        logger.info("Docker context prepared at: {}", tempDir);
    }

    static File prepareContext(File tempDir, String dataResourcePath) throws IOException, URISyntaxException {
        var dockerResourceUrl = TempComposeBasaltrockContainer.class.getResource("/opensearch/docker");

        var uri = dockerResourceUrl.toURI();
        var dockerPath = getDockerPath(uri);

        try (Stream<Path> paths = Files.walk(dockerPath)) {
            paths.filter(Files::isRegularFile).forEach(source -> {
                var relative = dockerPath.relativize(source);
                var target = tempDir.toPath().resolve(relative.toString());
                try {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        FileUtils.copyDirectory(new File(dataResourcePath), new File(tempDir, "data"));
        return new File(tempDir, "docker-compose.yml");
    }

    static @NotNull Path getDockerPath(URI uri) throws IOException {
        if ("jar".equals(uri.getScheme())) {
            var fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
            return fs.getPath("/opensearch/docker");
        }
        return Path.of(uri);
    }

    @Override
    public void stop() {
        super.stop();
        if (tempDir.exists()) {
            FileUtils.deleteQuietly(tempDir);
            logger.debug("Cleaned up temp directory: {}", tempDir);
        }
    }
}