package org.nanonative.ab.devconsole.service;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeInfo;
import berlin.yuna.typemap.model.TypeList;
import berlin.yuna.typemap.model.TypeMap;
import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.nano.core.NanoBase;
import org.nanonative.nano.core.model.NanoThread;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.ContentType;
import org.nanonative.nano.services.http.model.HttpObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import berlin.yuna.typemap.model.ConcurrentTypeSet;
import org.nanonative.nano.services.logging.LogFormatRegister;

import static berlin.yuna.typemap.logic.JsonEncoder.toJson;
import static org.nanonative.ab.devconsole.util.ResponseHelper.responseOk;
import static org.nanonative.nano.core.model.Context.EVENT_APP_HEARTBEAT;
import static org.nanonative.nano.core.model.Context.EVENT_CONFIG_CHANGE;
import static org.nanonative.nano.helper.config.ConfigRegister.registerConfig;
import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;
import static org.nanonative.nano.services.logging.LogService.EVENT_LOGGING;

public class DevConsoleService extends Service {

    // Config keys
    public static final String CONFIG_DEV_CONSOLE_MAX_EVENTS = registerConfig("dev_console_max_events", "Max number of events to retain in memory");
    public static final String CONFIG_DEV_CONSOLE_MAX_LOGS = registerConfig("dev_console_max_logs", "Max number of logs to retain in memory");
    public static final String CONFIG_DEV_CONSOLE_URL = registerConfig("dev_console_url", "Endpoint for the dev console ui");

    // UI resource paths
    public static final String UI_BASE_DIR = "/ui";
    public static final String UI_RESOURCE_FILE = "ui-files.txt";

    // Constants
    public static final String BASE_URL = "/dev-console";
    public static final int DEFAULT_MAX_EVENTS = 1000;
    public static final int DEFAULT_MAX_LOGS = 1000;
    public static final String DEFAULT_UI_URL = "/ui";
    public static final String DEV_EVENTS_URL = "/events";
    public static final String DEV_INFO_URL = "/system-info";
    public static final String DEV_LOGS_URL = "/logs";
    public static final String DEV_CONFIG_URL = "/config";

    // Configurable fields
    protected String basePath;
    protected Integer maxEvents;
    protected Integer maxLogs;

    // Data structures
    public static final Map<String, String> STATIC_FILES = new HashMap<>();
    public static final Deque<Event<?, ?>> eventHistory = new ConcurrentLinkedDeque<>();
    public static final Deque<String> logHistory = new ConcurrentLinkedDeque<>();
    public static final ConcurrentTypeSet subscribedChannels = new ConcurrentTypeSet();
    public static final AtomicInteger totalEvents = new AtomicInteger(0);

    public static Formatter logFormatter = LogFormatRegister.getLogFormatter("console");

    @Override
    public void start() {
        try {
            checkForNewChannelsAndSubscribe();
            loadStaticFiles();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        context.subscribeEvent(EVENT_APP_HEARTBEAT, __ -> checkForNewChannelsAndSubscribe());
        context.info(() -> "[{}] started at {} ", name(), BASE_URL + basePath);
    }

    @SuppressWarnings("ConstantConditions")
    protected void checkForNewChannelsAndSubscribe() {
        NanoBase.EVENT_CHANNELS.values().forEach(channel -> {
            if (channel != EVENT_APP_HEARTBEAT && subscribedChannels.add(channel)) {
                context.subscribeEvent(channel, this::recordEvent);
            }
        });
    }

    protected void recordEvent(Event<?, ?> event) {
        if (!event.channel().equals(EVENT_LOGGING)) {
            if (eventHistory.size() >= maxEvents) {
                removeLastNElements(eventHistory, eventHistory.size() - maxEvents + 1);
            }
            event.put("createdTs", Instant.now());
            eventHistory.addFirst(event);
        } else {
            if (logHistory.size() >= maxLogs) {
                removeLastNElements(logHistory, logHistory.size() - maxLogs + 1);
            }
            logHistory.addFirst(logFormatter.format((LogRecord) event.payload()));
        }
        totalEvents.incrementAndGet();
    }

    @Override
    public void configure(TypeMapI<?> configs, TypeMapI<?> merged) {
        this.maxEvents = configs.asIntOpt(CONFIG_DEV_CONSOLE_MAX_EVENTS).orElse(merged.asIntOpt(CONFIG_DEV_CONSOLE_MAX_EVENTS).orElse(DEFAULT_MAX_EVENTS));
        this.maxLogs = configs.asIntOpt(CONFIG_DEV_CONSOLE_MAX_LOGS).orElse(merged.asIntOpt(CONFIG_DEV_CONSOLE_MAX_LOGS).orElse(DEFAULT_MAX_LOGS));
        this.basePath = configs.asStringOpt(CONFIG_DEV_CONSOLE_URL).orElse(merged.asStringOpt(CONFIG_DEV_CONSOLE_URL).orElse(DEFAULT_UI_URL));

        if (maxEvents < eventHistory.size()) {
            removeLastNElements(eventHistory, eventHistory.size() - maxEvents);
        }
        if (maxLogs < logHistory.size()) {
            removeLastNElements(logHistory, logHistory.size() - maxLogs);
        }
    }

    @Override
    public Object onFailure(Event event) {
        return null;
    }

    @Override
    public void stop() {
        context.info(() -> "[{}] stopped.", name());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onEvent(Event<?, ?> event) {
        event.channel(EVENT_HTTP_REQUEST).flatMap(Event::payloadOpt).ifPresent(request -> handleHttpRequest((Event<HttpObject, HttpObject>) event));
    }

    protected void handleHttpRequest(final Event<HttpObject, HttpObject> event) {
        switch (event.payload().methodType()) {
            case GET -> handleGet(event);
            case PATCH -> handlePatch(event);
        }
    }

    protected void handleGet(Event<HttpObject, HttpObject> event) {
        if (event.payload().pathMatch(BASE_URL + DEV_INFO_URL)) {
            event.respond(responseOk(event.payload(), toJson(getSystemInfo()), ContentType.APPLICATION_JSON));
        } else if (event.payload().pathMatch(BASE_URL + DEV_EVENTS_URL)) {
            event.respond(responseOk(event.payload(), getEventList(), ContentType.APPLICATION_JSON));
        } else if (event.payload().pathMatch(BASE_URL + DEV_LOGS_URL)) {
            event.respond(responseOk(event.payload(), toJson(logHistory), ContentType.APPLICATION_JSON));
        } else if (event.payload().pathMatch(BASE_URL + DEV_CONFIG_URL)) {
            event.respond(responseOk(event.payload(), getConfig(), ContentType.APPLICATION_JSON));
        } else if (event.payload().pathMatch(BASE_URL + basePath)) {
            event.respond(responseOk(event.payload(), STATIC_FILES.get("index.html"), ContentType.TEXT_HTML));
        } else if (event.payload().pathMatch(BASE_URL + "/{fileName}")) {
            String fileName = event.payload().pathParam("fileName");
            if (STATIC_FILES.containsKey(fileName)) {
                event.respond(responseOk(event.payload(), STATIC_FILES.get(fileName), getTypeFromFileExt(fileName)));
            }
        }
    }

    /* Experimental: Can replace the above handleGet(Event) method. However, this is less performant than if/else.
    protected void handleGet1(Event<HttpObject, HttpObject> event) {
        switch (match(event.payload())) {
            case DevInfo __ ->
                event.respond(responseOk(event.payload(), toJson(getSystemInfo()), ContentType.APPLICATION_JSON));
            case DevEvents __ ->
                event.respond(responseOk(event.payload(), getEventList(), ContentType.APPLICATION_JSON));
            case DevLogs __ ->
                event.respond(responseOk(event.payload(), toJson(logHistory), ContentType.APPLICATION_JSON));
            case DevConfig __ -> event.respond(responseOk(event.payload(), getConfig(), ContentType.APPLICATION_JSON));
            case DevHtml __ ->
                event.respond(responseOk(event.payload(), STATIC_FILES.get("index.html"), ContentType.TEXT_HTML));
            case DevUiFile fr -> {
                if (STATIC_FILES.containsKey(fr.fileName())) {
                    event.respond(responseOk(event.payload(), STATIC_FILES.get(fr.fileName()), getTypeFromFileExt(fr.fileName())));
                }
            }
            case NoMatch __ -> {}
        }
    }

    protected RoutesMatch match(HttpObject request) {
        if (request.pathMatch(BASE_URL + DEV_INFO_URL)) return new DevInfo();
        if (request.pathMatch(BASE_URL + DEV_EVENTS_URL)) return new DevEvents();
        if (request.pathMatch(BASE_URL + DEV_LOGS_URL)) return new DevLogs();
        if (request.pathMatch(BASE_URL + DEV_CONFIG_URL)) return new DevConfig();
        if (request.pathMatch(BASE_URL + basePath)) return new DevHtml();
        if (request.pathMatch(BASE_URL + "/{fileName}")) return new DevUiFile(request.pathParam("fileName"));
        return new NoMatch();
    }*/

    protected void handlePatch(Event<HttpObject, HttpObject> event) {
        if (event.payload().pathMatch(BASE_URL + DEV_CONFIG_URL)) {
            event.respond(responseOk(event.payload(), updateConfig(event.payload().bodyAsJson()), event.payload().contentType()));
        }
    }

    protected String updateConfig(TypeInfo<?> request) {
        Map<String, Object> configChangeMap = new HashMap<>();
        if (request.isPresent("maxEvents")) {
            configChangeMap.put(CONFIG_DEV_CONSOLE_MAX_EVENTS, request.asInt("maxEvents"));
        }
        if (request.isPresent("maxLogs")) {
            configChangeMap.put(CONFIG_DEV_CONSOLE_MAX_LOGS, request.asInt("maxLogs"));
        }
        if (request.isPresent("baseUrl")) {
            configChangeMap.put(CONFIG_DEV_CONSOLE_URL, request.asString("baseUrl"));
        }
        context.newEvent(EVENT_CONFIG_CHANGE, () -> configChangeMap).broadcast(true).async(true).send();
        return toJson(configChangeMap);
    }

    protected String getConfig() {
        return new TypeMap(Map.of(
            "baseUrl", basePath,
            "maxEvents", maxEvents,
            "maxLogs", maxLogs)).toJson();
    }

    public String getEventList() {
        TypeList eventsList = new TypeList();
        for (Event<?, ?> e : eventHistory) {
            LinkedTypeMap eventMap = new LinkedTypeMap()
                .putR("channel", e.channel().name())
                .putR("isAck", e.isAcknowledged())
                .putR("isBroadcast", e.isBroadcast())
                .putR("eventTimestamp", e.get("createdTs"))
                .putR("payload", e.payload() != null ? (String.valueOf(e.payload()).length() > 256 ? String.valueOf(e.payload()).substring(0, 256) + "…" : String.valueOf(e.payload())) : "")
                .putR("response", e.response() != null ? (String.valueOf(e.response()).length() > 256 ? String.valueOf(e.response()).substring(0, 256) + "…" : String.valueOf(e.response())) : "");
            eventsList.add(eventMap);
        }
        return eventsList.toJson();
    }

    public LinkedTypeMap getSystemInfo() {
        return new LinkedTypeMap()
            .putR("pid", context.nano().pid())
            .putR("usedMemory", context.nano().usedMemoryMB() + " MB")
            .putR("services", context.services().size())
            .putR("serviceNames", context.services().stream().map(Service::name).toList())
            .putR("schedulers", context.nano().schedulers().size())
            .putR("listeners", context.nano().listeners().values().stream().mapToLong(Collection::size).sum())
            .putR("heapMemory", context.nano().heapMemoryUsage())
            .putR("os", System.getProperty("os.name") + " - " + System.getProperty("os.version"))
            .putR("arch", System.getProperty("os.arch"))
            .putR("java", System.getProperty("java.version"))
            .putR("cores", Runtime.getRuntime().availableProcessors())
            .putR("threadsNano", NanoThread.activeNanoThreads())
            .putR("threadsActive", NanoThread.activeCarrierThreads())
            .putR("otherThreads", ManagementFactory.getThreadMXBean().getThreadCount() - NanoThread.activeCarrierThreads())
            .putR("totalEvents", totalEvents.get())
            .putR("timestamp", String.valueOf(Instant.now()));
    }

    public static void loadStaticFiles() throws IOException {
        List<String> fileNames = loadStaticFile("/" + UI_RESOURCE_FILE).lines().map(String::trim).filter(fileName -> !fileName.isBlank()).toList();
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

    public static ContentType getTypeFromFileExt(String path) {
        if (!path.contains(".")) {
            return ContentType.TEXT_PLAIN;
        }
        String ext = path.substring(path.lastIndexOf('.') + 1);
        return switch (ext) {
            case "html" -> ContentType.TEXT_HTML;
            case "css" -> ContentType.TEXT_CSS;
            case "js" -> ContentType.APPLICATION_JAVASCRIPT;
            default -> ContentType.TEXT_PLAIN;
        };
    }

    public static void removeLastNElements(Deque<?> deque, final int N) {
        for (int i = 0; i < N; i++) {
            deque.removeLast();
        }
    }
}
