package org.nanonative.ab.devconsole.service;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeList;
import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.ab.devconsole.model.EventWrapper;
import org.nanonative.nano.core.NanoBase;
import org.nanonative.nano.core.model.NanoThread;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.ExRunnable;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.ContentType;
import org.nanonative.nano.services.http.model.HttpObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import berlin.yuna.typemap.logic.JsonEncoder;
import berlin.yuna.typemap.model.ConcurrentTypeSet;
import org.nanonative.nano.services.logging.LogFormatRegister;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.nanonative.ab.devconsole.util.ResponseHelper.problem;
import static org.nanonative.ab.devconsole.util.ResponseHelper.responseOk;
import static org.nanonative.nano.core.model.Context.EVENT_APP_HEARTBEAT;
import static org.nanonative.nano.helper.config.ConfigRegister.registerConfig;
import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;
import static org.nanonative.nano.services.logging.LogService.EVENT_LOGGING;

public class DevConsoleService extends Service {

    // Static final config keys
    public static final String CONFIG_DEV_CONSOLE_MAX_EVENTS = registerConfig("dev_console_max_events", "Max number of events to retain in memory");
    public static final String CONFIG_DEV_CONSOLE_MAX_LOGS = registerConfig("dev_console_max_logs", "Max number of logs to retain in memory");
    public static final String CONFIG_DEV_CONSOLE_URL = registerConfig("dev_console_url", "Endpoint for the dev console ui");

    // Static UI resource paths
    private static final String UI_BASE_DIR = "/ui";
    private static final String UI_RESOURCE_FILE = "ui-files.txt";

    // Final constants
    private final String BASE_URL = "/dev-console";
    private final int DEFAULT_MAX_EVENTS = 1000;
    private final int DEFAULT_MAX_LOGS = 1000;
    private final String DEFAULT_UI_URL = "/ui";
    private final String DEV_EVENTS_URL = "/events";
    private final String DEV_INFO_URL = "/system-info";
    private final String DEV_LOGS_URL = "/logs";

    // Configurable fields
    private String basePath;
    private Integer maxEvents;
    private Integer maxLogs;

    // Data structures
    private static final Map<String, String> STATIC_FILES = new HashMap<>();
    private final Deque<EventWrapper> eventHistory = new ConcurrentLinkedDeque<>();
    private final Deque<Object> logHistory = new ConcurrentLinkedDeque<>();
    private final ConcurrentTypeSet subscribedChannels = new ConcurrentTypeSet();
    private final AtomicInteger totalEvents = new AtomicInteger(0);

    public List<EventWrapper> getEventHistory() {
        // send a copy of current eventHistory - to be used by external services
        return new ArrayList<>(eventHistory);
    }

    @Override
    public void start() {
        try {
            loadStaticFiles();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        context.run(checkForNewChannelsAndSubscribe(), 0, 5, SECONDS);
        context.info(() -> "[{}] started at {} ", name(), BASE_URL + basePath);
    }

    private ExRunnable checkForNewChannelsAndSubscribe() {
        return () -> NanoBase.EVENT_CHANNELS.values().forEach(channel -> {
            if (channel != EVENT_APP_HEARTBEAT && subscribedChannels.add(channel)) {
                context.subscribeEvent(channel, this::recordEvent);
            }
        });
    }

    @Override
    public void stop() {
        context.info(() -> "[{}] stopped.", name());
    }

    @Override
    public Object onFailure(Event event) {
        return null;
    }

    private void recordEvent(Event<?, ?> event) {
        if (eventHistory.size() >= maxEvents) {
            eventHistory.removeLast();
        }
        eventHistory.addFirst(new EventWrapper(event, Instant.now()));

        // Preserve logs in memory
        event.channel(EVENT_LOGGING).ifPresent(ev -> {
            if (logHistory.size() >= maxLogs) {
                logHistory.removeLast();
            }
            logHistory.addFirst(LogFormatRegister.getLogFormatter("console").format(ev.payload()));
        });
        totalEvents.incrementAndGet();
    }

    @Override
    public void onEvent(Event<?, ?> event) {
        event.channel(EVENT_HTTP_REQUEST).ifPresent(ev ->
            ev.payloadOpt()
                .filter(HttpObject::isMethodGet)
                .filter(request -> request.pathMatch(BASE_URL + DEV_INFO_URL))
                .ifPresentOrElse(request -> ev.respond(responseOk(request, JsonEncoder.toJson(getSystemInfo()), ContentType.APPLICATION_JSON)),
                    () -> ev.payloadOpt()
                        .filter(HttpObject::isMethodGet)
                        .filter(request -> request.pathMatch(BASE_URL + DEV_EVENTS_URL))
                        .ifPresentOrElse(request -> ev.respond(responseOk(request, getEventList(), ContentType.APPLICATION_JSON)),
                            () -> ev.payloadOpt()
                                .filter(HttpObject::isMethodGet)
                                .filter(request -> request.pathMatch(BASE_URL + DEV_LOGS_URL))
                                .ifPresentOrElse(request -> ev.respond(responseOk(request, JsonEncoder.toJson(logHistory), ContentType.APPLICATION_JSON)),
                                    () -> ev.payloadOpt()
                                        .filter(HttpObject::isMethodGet)
                                        .filter(request -> request.pathMatch(BASE_URL + basePath))
                                        .ifPresentOrElse(request -> ev.respond(responseOk(ev.payload(), STATIC_FILES.get("index.html"), ContentType.TEXT_HTML)),
                                            () -> ev.payloadOpt()
                                                .filter(HttpObject::isMethodGet)
                                                .filter(request -> request.pathMatch(BASE_URL + "/{fileName}"))
                                                .map(request -> request.pathParam("fileName"))
                                                .filter(STATIC_FILES::containsKey)
                                                .ifPresentOrElse(fileName -> ev.respond(responseOk(ev.payload(), STATIC_FILES.get(fileName), getTypeFromFileExt(fileName))),
                                                    () -> ev.respond(problem(ev.payload(), 404, "Unknown Universe"))))))));
    }


    @Override
    public void configure(TypeMapI<?> configs, TypeMapI<?> merged) {
        this.maxEvents = configs.asIntOpt(CONFIG_DEV_CONSOLE_MAX_EVENTS).orElse(merged.asIntOpt(CONFIG_DEV_CONSOLE_MAX_EVENTS).orElse(DEFAULT_MAX_EVENTS));
        this.maxLogs = configs.asIntOpt(CONFIG_DEV_CONSOLE_MAX_LOGS).orElse(merged.asIntOpt(CONFIG_DEV_CONSOLE_MAX_LOGS).orElse(DEFAULT_MAX_LOGS));
        this.basePath = configs.asStringOpt(CONFIG_DEV_CONSOLE_URL).orElse(merged.asStringOpt(CONFIG_DEV_CONSOLE_URL).orElse(DEFAULT_UI_URL));
    }

    private String getEventList() {
        TypeList eventsList = new TypeList();
        for (EventWrapper e : eventHistory) {
            LinkedTypeMap eventMap = new LinkedTypeMap()
                .putR("channel", e.event().channel().name())
                .putR("isAck", e.event().isAcknowledged())
                .putR("isBroadcast", e.event().isBroadcast())
                .putR("eventTimestamp", Objects.toString(e.timestamp(), Instant.EPOCH.toString()))
                .putR("payload", e.event().payload() != null ? (String.valueOf(e.event().payload()).length() > 256 ? String.valueOf(e.event().payload()).substring(0, 256) + "…" : String.valueOf(e.event().payload())) : "")
                .putR("response", e.event().response() != null ? (String.valueOf(e.event().response()).length() > 256 ? String.valueOf(e.event().response()).substring(0, 256) + "…" : String.valueOf(e.event().response())) : "");
            eventsList.add(eventMap);
        }
        return eventsList.toJson();
    }

    private LinkedTypeMap getSystemInfo() {
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

    private static String loadStaticFile(String fileName) throws IOException {
        InputStream in = Objects.requireNonNull(
            DevConsoleService.class.getResourceAsStream(UI_BASE_DIR + "/" + fileName),
            fileName + " not found in resources");
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static ContentType getTypeFromFileExt(String path) {
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
}
