package org.ab.devconsole.service;

import berlin.yuna.typemap.model.TypeMapI;
import org.ab.devconsole.model.EventWrapper;
import org.nanonative.nano.core.NanoBase;
import org.nanonative.nano.core.model.NanoThread;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.config.ConfigRegister;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;
import static  org.nanonative.nano.core.model.Context.EVENT_APP_HEARTBEAT;

public class DevConsoleService extends Service {

    // We keep a thread-safe list to track events.
    // Limit the history size to keep memory usage in check.
    private final List<org.ab.devconsole.model.EventWrapper> eventHistory = new LinkedList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Set<Integer> subscribedChannels = ConcurrentHashMap.newKeySet();
    private static final int DEFAULT_MAX_EVENTS = 1000;
    public static final String CONFIG_DEV_CONSOLE_MAX_EVENTS =
            ConfigRegister.registerConfig("dev_console_max_events", "Max number of events to retain in the DevConsoleService");
    private final Object lock = new Object();
    private Integer maxEvents;

    @Override
    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            NanoBase.EVENT_TYPES.keySet().forEach(channelId -> {
                if (subscribedChannels.add(channelId)) {
                    context.subscribeEvent(channelId, this::recordEvent);
                }
            });
        }, 0, 5, TimeUnit.SECONDS);
        context.info(() -> "[" + name() + "] started at /dev/ui");
    }

    @Override
    public void stop() {
        context.info(() -> "[" + name() + "] stopped.");
    }

    @Override
    public Object onFailure(Event event) {
        return null;
    }

    private void recordEvent(Event event) {
        if(event.channelId() == EVENT_APP_HEARTBEAT)
            return;
        synchronized (lock) {
            if (eventHistory.size() >= maxEvents) {
                eventHistory.removeLast();
            }
            eventHistory.addFirst(EventWrapper.builder()
                    .event(event)
                    .timestamp(Instant.now())
                    //   .sourceService(event.asString("source")) TODO: Get event source
                    .build());
        }
    }

    @Override
    public void onEvent(Event event) {
        event.payloadOpt(HttpObject.class).ifPresent(request -> {
            if (request.pathMatch("/dev/system-info")) {
                event.acknowledge();
                String systemInfoJson = formatToJson(getSystemInfo());
                request.response()
                        .statusCode(200)
                        .header("Content-Type", "application/json")
                        .body(systemInfoJson)
                        .respond(event);
                return;
            }

            if (request.pathMatch("/dev/events")) {
                event.acknowledge();
                String eventsJson = formatListToJsonArray(getEventList());
                request.response()
                        .statusCode(200)
                        .header("Content-Type", "application/json")
                        .body(eventsJson)
                        .respond(event);
                return;
            }
            if (request.pathMatch("/dev/ui")) {
                event.acknowledge();
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("index.html")) {
                    if (in == null) {
                        request.response().statusCode(404).body("UI not found").respond(event);
                        return;
                    }

                    String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);

                    request.response()
                            .statusCode(200)
                            .header("Content-Type", "text/html")
                            .body(html)
                            .respond(event);

                } catch (IOException e) {
                    request.response().statusCode(500).body("Error loading UI").respond(event);
                }
            }
        });
    }

    /**
     * Formats a map into a JSON string.
     *
     * @param inputMap < String, Object> The record to format.
     * @return The map as a JSON string.
     */
    private String formatToJson(final Map<String, Object> inputMap) {
        return "{" + inputMap.entrySet().stream()
                .map(entry -> "\"" + escapeJson(entry.getKey()) + "\":" + toJsonValue(entry.getValue()))
                .collect(Collectors.joining(",")) + "}";
    }

    private String toJsonValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Map) {
            return formatToJson((Map<String, Object>) value);
        } else {
            return "\"" + escapeJson(value.toString()) + "\"";
        }
    }

    private String escapeJson(String str) {
        return str
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String formatListToJsonArray(final List<Map<String, Object>> inputList) {
        return "[" + inputList.stream()
                .map(this::formatToJson)
                .collect(Collectors.joining(","))
                + "]";
    }

    @Override
    public void configure(TypeMapI<?> configs, TypeMapI<?> merged) {
        this.maxEvents = merged.asIntOpt(CONFIG_DEV_CONSOLE_MAX_EVENTS).orElse(DEFAULT_MAX_EVENTS);
    }

    private List<Map<String, Object>> getEventList() {
        List<Map<String, Object>> eventList = new ArrayList<>();
        for (EventWrapper e : eventHistory) {
            eventList.add(Map.of(
                    "channel", Objects.toString(e.getEvent() != null ? e.getEvent().channel() : "UNKNOWN"),
                    "isAck", e.getEvent() != null && e.getEvent().isAcknowledged(),
                    "eventTimestamp", Objects.toString(e.getTimestamp(), Instant.EPOCH.toString()),
                    "devEventId", Objects.toString(e.getEvent() != null ? e.getEvent().asUUID() : "N/A"),
                    "parentChannel", Objects.toString(
                            NanoBase.EVENT_TYPES.get(e.getEvent() != null ? e.getEvent().channelIdOrg() : null),
                            "UNKNOWN"
                    ),
                    "payload", e.getEvent() != null
                            ? e.getEvent().payloadOpt(Object.class).orElse("NONE")
                            : "NONE"
            ));
        }
        return eventList;
    }

    private Map<String, Object> getSystemInfo() {
        final long allThreads = NanoThread.activeCarrierThreads();
        final Map<String, Object> info = Map.ofEntries(
                Map.entry("pid", context.nano().pid()),
                Map.entry("usedMemory", context.nano().usedMemoryMB() + " MB"),
                Map.entry("services", context.services().size()),
                Map.entry("serviceNames", context.services().stream().map(Service::name).toList()),
                Map.entry("schedulers", context.nano().schedulers().size()),
                Map.entry("listeners", context.nano().listeners().values().stream().mapToLong(Collection::size).sum()),
                Map.entry("heapMemory", context.nano().heapMemoryUsage()),
                Map.entry("os", System.getProperty("os.name") + " - " + System.getProperty("os.version")),
                Map.entry("arch", System.getProperty("os.arch")),
                Map.entry("java", System.getProperty("java.version")),
                Map.entry("cores", Runtime.getRuntime().availableProcessors()),
                Map.entry("threadsNano", NanoThread.activeNanoThreads()),
                Map.entry("threadsActive", NanoThread.activeCarrierThreads()),
                Map.entry("otherThreads", ManagementFactory.getThreadMXBean().getThreadCount() - NanoThread.activeCarrierThreads()),
                Map.entry("host", context.nano().hostname()),
                Map.entry("timestamp", Instant.now().toString())
        );
        return info;
    }

    public List<EventWrapper> getEventHistory() {
        // send a copy of the LinkedList to be used by external services
        return new ArrayList<>(eventHistory);
    }
}