package org.nanonative.devconsole.util;

import org.nanonative.devconsole.service.DevConsoleService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class UiHelper {

    // UI resource paths
    public static final String UI_BASE_DIR = "/ui";
    public static final String UI_RESOURCE_FILE = "ui-files.txt";

    public static Map<String, String> STATIC_FILES = new ConcurrentHashMap<>();

    private UiHelper() {}

    public static void loadStaticFiles() throws IOException {
        List<String> fileNames = loadStaticFile(UI_RESOURCE_FILE).lines().map(String::trim).filter(fileName -> !fileName.isBlank()).toList();
        for (String file : fileNames) {
            if (!STATIC_FILES.containsKey(file))
                STATIC_FILES.put(file, loadStaticFile(file));
        }
    }

    public static String loadStaticFile(String fileName) throws IOException {
        InputStream in = Objects.requireNonNull(
            DevConsoleService.class.getResourceAsStream(UI_BASE_DIR + "/" + fileName),
            fileName + " not found in resources");
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
