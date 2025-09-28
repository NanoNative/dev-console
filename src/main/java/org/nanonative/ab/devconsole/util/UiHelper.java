package org.nanonative.ab.devconsole.util;

import org.nanonative.ab.devconsole.service.DevConsoleService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class UiHelper {

    // UI resource paths
    public static final String UI_BASE_DIR = "/ui";
    public static final String UI_RESOURCE_FILE = "ui-files.txt";

    public static final Map<String, String> STATIC_FILES = new HashMap<>();

    private UiHelper() {}

    public static void loadStaticFiles() throws IOException {
        List<String> fileNames = loadStaticFile(UI_RESOURCE_FILE).lines().map(String::trim).filter(fileName -> !fileName.isBlank()).toList();
        for (String file : fileNames) {
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
