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

    private final Deque<EventWrapper> eventHistory = new ConcurrentLinkedDeque<>();
    private final Deque<Object> logHistory = new ConcurrentLinkedDeque<>();
    private final ConcurrentTypeSet subscribedChannels = new ConcurrentTypeSet();
    public static final String CONFIG_DEV_CONSOLE_MAX_EVENTS = registerConfig("dev_console_max_events", "Max number of events to retain in the DevConsoleService");
    public static final String CONFIG_DEV_CONSOLE_URL = registerConfig("dev_console_url", "Endpoint for the dev console ui");
    private Integer maxEvents;
    private String basePath;
    private final int DEFAULT_MAX_EVENTS = 1000;
    private final String baseUrl = "/dev-console";
    private final String DEFAULT_URL = "/dev-console/ui";
    private final String DEV_EVENTS_URL = "/dev-console/events";
    private final String DEV_INFO_URL = "/dev-console/system-info";
    private final String DEV_LOGS_URL = "/dev-console/logs";
    private static final String HTML_PATH = "/index.html";
    private static final String CSS_PATH = "/style.css";
    private static final String JS_PATH = "/script.js";

    private static final Map<String, String> STATIC_FILES = new HashMap<>();

    private static void loadStaticFile(String path) throws IOException {
        InputStream in = Objects.requireNonNull(
            DevConsoleService.class.getResourceAsStream(path),
            String.format("%s not found in resources", path));
        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        STATIC_FILES.put(path, content);
    }

    @Override
    public void start() {
        try {
            loadStaticFile(HTML_PATH);
            loadStaticFile(CSS_PATH);
            loadStaticFile(JS_PATH);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        context.run(checkForNewChannelsAndSubscribe(), 0, 5, SECONDS);
        context.info(() -> "[{}] started at {} ", name(), basePath);
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
                if (logHistory.size() >= maxEvents) {
                    logHistory.removeLast();
                }
                logHistory.addFirst(LogFormatRegister.getLogFormatter("console").format(ev.payload()));
            });
    }

    @Override
    public void onEvent(Event<?, ?> event) {
        event.channel(EVENT_HTTP_REQUEST).filter(ev -> ev.payload().pathMatch(DEV_LOGS_URL)).ifPresent(this::fetchSystemLogs);
        event.channel(EVENT_HTTP_REQUEST).filter(ev -> ev.payload().pathMatch(DEV_INFO_URL)).ifPresent(this::fetchSystemInfo);
        event.channel(EVENT_HTTP_REQUEST).filter(ev -> ev.payload().pathMatch(DEV_EVENTS_URL)).ifPresent(this::fetchSystemEvents);
        event.channel(EVENT_HTTP_REQUEST).filter(ev -> ev.payload().pathMatch(baseUrl + CSS_PATH)).ifPresent(DevConsoleService::fetchCss);
        event.channel(EVENT_HTTP_REQUEST).filter(ev -> ev.payload().pathMatch(baseUrl + JS_PATH)).ifPresent(DevConsoleService::fetchJs);
        event.channel(EVENT_HTTP_REQUEST).filter(ev -> ev.payload().pathMatch(basePath)).ifPresent(DevConsoleService::fetchHtml);
    }


    @Override
    public void configure(TypeMapI<?> configs, TypeMapI<?> merged) {
        this.maxEvents = configs.asIntOpt(CONFIG_DEV_CONSOLE_MAX_EVENTS).orElse(merged.asIntOpt(CONFIG_DEV_CONSOLE_MAX_EVENTS).orElse(DEFAULT_MAX_EVENTS));
        this.basePath = configs.asStringOpt(CONFIG_DEV_CONSOLE_URL).orElse(merged.asStringOpt(CONFIG_DEV_CONSOLE_URL).orElse(DEFAULT_URL));
    }

    private String buildEventList() {
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
            .putR("timestamp", JsonEncoder.toJson(Instant.now()));
    }

    private void fetchSystemLogs(Event<HttpObject, HttpObject> event) {
        event.payloadOpt().filter(HttpObject::isMethodGet)
            .ifPresent(request -> {
                String content = JsonEncoder.toJson(logHistory);
                if (content == null) {
                    event.respond(problem(request, 404, "Logs failed to load..."));
                    return;
                }
                event.respond(responseOk(request, content, ContentType.APPLICATION_JSON));
            });
    }

    private void fetchSystemInfo(Event<HttpObject, HttpObject> event) {
        event.payloadOpt().filter(HttpObject::isMethodGet)
            .ifPresent(request -> {
                String content = JsonEncoder.toJson(getSystemInfo());
                if (content == null) {
                    event.respond(problem(request, 404, "System Info failed to load..."));
                    return;
                }
                event.respond(responseOk(request, content, ContentType.APPLICATION_JSON));
            });
    }

    private void fetchSystemEvents(Event<HttpObject, HttpObject> event) {
        event.payloadOpt().filter(HttpObject::isMethodGet)
            .ifPresent(request -> {
                String content = buildEventList();
                event.respond(responseOk(request, content, ContentType.APPLICATION_JSON));
            });
    }

    private static void fetchJs(Event<HttpObject, HttpObject> event) {
        event.payloadOpt().filter(HttpObject::isMethodGet)
            .ifPresent(request -> {
                String content = STATIC_FILES.get(JS_PATH);
                if (content == null) {
                    event.respond(problem(request, 404, String.format("Not found: %s", JS_PATH)));
                    return;
                }
                event.respond(responseOk(request, content, ContentType.APPLICATION_JAVASCRIPT));
            });
    }

    private static void fetchCss(Event<HttpObject, HttpObject> event) {
        event.payloadOpt().filter(HttpObject::isMethodGet)
            .ifPresent(request -> {
                String content = STATIC_FILES.get(CSS_PATH);
                if (content == null) {
                    event.respond(problem(request, 404, String.format("Not found: %s", CSS_PATH)));
                    return;
                }
                event.respond(responseOk(request, content, ContentType.TEXT_CSS));
            });
    }

    private static void fetchHtml(Event<HttpObject, HttpObject> event) {
        event.payloadOpt().filter(HttpObject::isMethodGet)
            .ifPresent(request -> {
                String content = STATIC_FILES.get(HTML_PATH);
                if (content == null) {
                    event.respond(problem(request, 404, String.format("Not found: %s", HTML_PATH)));
                    return;
                }
                event.respond(responseOk(request, content, ContentType.TEXT_HTML));
            });
    }

    public List<EventWrapper> getEventHistory() {
        // send a copy of current eventHistory - to be used by external services
        return new ArrayList<>(eventHistory);
    }
}
