package org.nanonative.devconsole.service;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeInfo;
import berlin.yuna.typemap.model.TypeList;
import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.devconsole.util.ClassInfo;
import org.nanonative.devconsole.util.DevConfig;
import org.nanonative.devconsole.util.DevEvents;
import org.nanonative.devconsole.util.DevHtml;
import org.nanonative.devconsole.util.DevInfo;
import org.nanonative.devconsole.util.DevLogs;
import org.nanonative.devconsole.util.DevService;
import org.nanonative.devconsole.util.DevUi;
import org.nanonative.devconsole.util.NoMatch;
import org.nanonative.devconsole.util.RoutesMatch;
import org.nanonative.devconsole.util.ServiceFactory;
import org.nanonative.nano.core.NanoBase;
import org.nanonative.nano.core.model.NanoThread;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Channel;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.HttpServer;
import org.nanonative.nano.services.http.model.ContentType;
import org.nanonative.nano.services.http.model.HttpObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import org.nanonative.nano.services.logging.LogFormatRegister;

import static berlin.yuna.typemap.logic.JsonEncoder.toJson;
import static java.lang.Thread.sleep;
import static org.nanonative.devconsole.util.ResponseHelper.getTypeFromFileExt;
import static org.nanonative.devconsole.util.ResponseHelper.responseOk;
import static org.nanonative.devconsole.util.SystemUtil.computeBaseUrl;
import static org.nanonative.devconsole.util.SystemUtil.getCpuUsagePercent;
import static org.nanonative.devconsole.util.UiHelper.STATIC_FILES;
import static org.nanonative.devconsole.util.UiHelper.loadStaticFiles;
import static org.nanonative.nano.core.model.Context.EVENT_APP_HEARTBEAT;
import static org.nanonative.nano.core.model.Context.EVENT_APP_SERVICE_REGISTER;
import static org.nanonative.nano.core.model.Context.EVENT_APP_SERVICE_UNREGISTER;
import static org.nanonative.nano.core.model.Context.EVENT_CONFIG_CHANGE;
import static org.nanonative.nano.helper.config.ConfigRegister.registerConfig;
import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;
import static org.nanonative.nano.services.logging.LogService.EVENT_LOGGING;

public class DevConsoleService extends Service {

    // Config keys
    public static final String CONFIG_DEV_CONSOLE_MAX_EVENTS = registerConfig("dev_console_max_events", "Max number of events to retain in memory");
    public static final String CONFIG_DEV_CONSOLE_MAX_LOGS = registerConfig("dev_console_max_logs", "Max number of logs to retain in memory");
    public static final String CONFIG_DEV_CONSOLE_URL = registerConfig("dev_console_url", "Endpoint for the dev console ui");

    // Constants
    public static final String BASE_URL = "/dev-console";
    public static final int DEFAULT_MAX_EVENTS = 1000;
    public static final int DEFAULT_MAX_LOGS = 1000;
    public static final String DEFAULT_UI_URL = "/ui";
    public static final String DEV_EVENTS_URL = "/events";
    public static final String DEV_INFO_URL = "/system-info";
    public static final String DEV_LOGS_URL = "/logs";
    public static final String DEV_CONFIG_URL = "/config";
    public static final String DEV_SERVICE_URL = "/service";
    public static final String SERVICES_PATH = "META-INF/io/github/absketches/plugin/services.properties";

    public static final Formatter logFormatter = LogFormatRegister.getLogFormatter("console");
    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    // Configurable fields
    protected String basePath;
    protected Integer maxEvents;
    protected Integer maxLogs;

    // Data structures
    protected Consumer<Event<Void, Void>> channelListener;
    protected final Map<Channel<?, ?>, Consumer<? extends Event<?, ?>>> eventListenerMap = new ConcurrentHashMap<>();
    protected final Deque<Event<?, ?>> eventHistory = new ConcurrentLinkedDeque<>();
    protected final Deque<String> logHistory = new ConcurrentLinkedDeque<>();
    protected final AtomicInteger totalEvents = new AtomicInteger(0);
    protected final ReentrantLock lock = new ReentrantLock();
    protected ServiceFactory svcFactory;

    // Exclude internal services which does not get affected on stop like LogService
    protected final Set<String> excludedServices = Set.of("LogService", "FileWatcher", "HttpServer", "HttpClient");

    // Populate this only once at startup - rest all read ops and not expected to run into concurrency issues
    protected Set<String> servicesIndex = new LinkedHashSet<>();


    @Override
    public void start() {
        checkForNewChannelsAndSubscribe();
        populateServiceIndex();
        try {
            loadStaticFiles();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        channelListener = context.subscribeEvent(EVENT_APP_HEARTBEAT, (ev, __) -> checkForNewChannelsAndSubscribe());
        // TODO: Remove - sleep added as HttpServer may take more time to initialize
        try {
            sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        context.info(() -> "[{}] started at {}{}{} ", name(), computeBaseUrl(context.nano().service(HttpServer.class)), BASE_URL, basePath);
    }

    protected void populateServiceIndex() {
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final List<URL> urls;
        try {
            urls = Collections.list(cl.getResources(SERVICES_PATH));
            if (!urls.isEmpty()) {
                Set<String> serviceFqcnList = fetchCorrectPropFile(urls);
                svcFactory = new ServiceFactory(new ArrayList<>(serviceFqcnList));
                servicesIndex = serviceFqcnList.stream().map(ServiceFactory::getSimpleName).collect(Collectors.toSet());
                servicesIndex.removeAll(excludedServices);
            }
        } catch (IOException ex) {
            context.warn(() -> "loadServicesIfPresent::IO exception: {}", ex);
        }
    }

    protected void checkForNewChannelsAndSubscribe() {
        NanoBase.EVENT_CHANNELS.values().forEach(channel -> {
            if (!eventListenerMap.containsKey(channel)) {
                final Consumer<? extends Event<?, ?>> listener = context.subscribeEvent(channel, (ev, __) -> recordEvent(ev));
                eventListenerMap.put(channel, listener);
            }
        });
    }

    @SuppressWarnings("unchecked")
    protected void recordEvent(final Event<?, ?> event) {
        totalEvents.incrementAndGet();
        // Exclude EVENT_APP_HEARTBEAT events from the list
        if (event.channel().equals(EVENT_APP_HEARTBEAT))
            return;
        // Exclude Dev console Http events from the list
        if (event.channel().equals(EVENT_HTTP_REQUEST) && event.payload() instanceof HttpObject payload) {
            RoutesMatch route = match(payload);
            if (!(route instanceof NoMatch)) {
                handleHttpRequest((Event<HttpObject, HttpObject>) event, route);
                return;
            }
        }

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
    }

    // Add dev console routes below
    protected RoutesMatch match(final HttpObject request) {
        if (request.pathMatch(BASE_URL + DEV_INFO_URL)) return new DevInfo();
        if (request.pathMatch(BASE_URL + DEV_EVENTS_URL)) return new DevEvents();
        if (request.pathMatch(BASE_URL + DEV_LOGS_URL)) return new DevLogs();
        if (request.pathMatch(BASE_URL + DEV_CONFIG_URL)) return new DevConfig();
        if (request.pathMatch(BASE_URL + DEV_SERVICE_URL + "/{serviceName}")) {
            final String svcName = request.pathParam("serviceName");
            if (getFilteredServices().stream().anyMatch(rSvc -> rSvc.name().equals(svcName)) || servicesIndex.stream().anyMatch(svcName::equals)) {
                return new DevService(svcName);
            }
        }
        if (request.pathMatch(BASE_URL + basePath)) return new DevHtml();
        if (request.pathMatch(BASE_URL + "/{fileName}")) {
            String fileName = request.pathParam("fileName");
            if (STATIC_FILES.containsKey(fileName))
                return new DevUi(fileName);
        }
        return new NoMatch();
    }

    protected void handleHttpRequest(final Event<HttpObject, HttpObject> event, final RoutesMatch route) {
        switch (event.payload().methodType()) {
            case GET -> handleGet(event, route);
            case PATCH -> handlePatch(event, route);
            case DELETE -> handleDelete(event, route);
        }
    }

    protected void handleGet(final Event<HttpObject, HttpObject> event, final RoutesMatch route) {
        switch (route) {
            case DevInfo __ ->
                event.respond(responseOk(event.payload(), toJson(getSystemInfo()), ContentType.APPLICATION_JSON));
            case DevEvents __ ->
                event.respond(responseOk(event.payload(), getEventList(), ContentType.APPLICATION_JSON));
            case DevLogs __ ->
                event.respond(responseOk(event.payload(), toJson(new ArrayList<>(logHistory)), ContentType.APPLICATION_JSON));
            case DevConfig __ -> event.respond(responseOk(event.payload(), getConfig(), ContentType.APPLICATION_JSON));
            case DevHtml __ ->
                event.respond(responseOk(event.payload(), STATIC_FILES.get("index.html"), ContentType.TEXT_HTML));
            case DevUi fileRequest ->
                event.respond(responseOk(event.payload(), STATIC_FILES.get(fileRequest.fileName()), getTypeFromFileExt(fileRequest.fileName())));
            case NoMatch __ -> {}
            default -> context.info(() -> "The HttpMethod for this endpoint is incorrect");
        }
    }

    protected void handlePatch(final Event<HttpObject, HttpObject> event, final RoutesMatch route) {
        switch (route) {
            case DevConfig __ ->
                event.respond(responseOk(event.payload(), updateConfig(event.payload().bodyAsJson()), event.payload().contentType()));
            case DevService devService -> startService(event, devService.name());
            default -> {}
        }
    }

    protected void startService(final Event<HttpObject, HttpObject> event, final String name) {
        if (getFilteredServices().stream().map(Service::name).anyMatch(svcName -> svcName.equals(name))) {
            event.error(new RuntimeException("{} already running"));
        } else if (null == svcFactory) {
            event.error(new RuntimeException("Service index does not exist"));
            context.error(() -> "This endpoint should not have been invoked - hacker alert");
        } else {
            ClassInfo info = svcFactory.getClassInfo(name);
            if (null != info) {
                Service service = svcFactory.newInstance(name, info.clazz());
                context.newEvent(EVENT_APP_SERVICE_REGISTER, () -> service).broadcast(true).async(true).send();
                event.respond(responseOk(event.payload(), "success:true", event.payload().contentType()));
            }
        }
    }

    protected void handleDelete(final Event<HttpObject, HttpObject> event, final RoutesMatch route) {
        if (route instanceof DevService) {
            Optional<Service> optService = getFilteredServices().stream().filter(svc -> svc.name().equals(((DevService) route).name())).findFirst();
            if (optService.isPresent()) {
                context.newEvent(EVENT_APP_SERVICE_UNREGISTER, optService::get).broadcast(true).async(true).send();
                event.respond(responseOk(event.payload(), "", event.payload().contentType()));
            } else {
                event.error(new RuntimeException("{} not running"));
            }
        }
    }

    protected String updateConfig(final TypeInfo<?> request) {
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
        return toJson(Map.of(
            "baseUrl", basePath,
            "maxEvents", maxEvents,
            "maxLogs", maxLogs));
    }

    public String getEventList() {
        final TypeList eventsList = new TypeList();
        for (Event<?, ?> e : new ArrayList<>(eventHistory)) {
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
        final LinkedTypeMap systemInfo = new LinkedTypeMap()
            .putR("pid", context.nano().pid())
            .putR("usedMemory", context.nano().usedMemoryMB() + " MB")
            .putR("runningServices", getFilteredServices().size())
            .putR("activeServices", getFilteredServices().stream().map(Service::name).toList())
            .putR("schedulers", context.nano().schedulers().size())
            .putR("listeners", getListenerCount(context.nano().listeners().values()))
            .putR("heapUsage", context.nano().heapMemoryUsage())
            .putR("os", System.getProperty("os.name") + " - " + System.getProperty("os.version"))
            .putR("arch", System.getProperty("os.arch"))
            .putR("java", System.getProperty("java.version"))
            .putR("cores", Runtime.getRuntime().availableProcessors())
            .putR("cpuUsage", getCpuUsagePercent())
            .putR("threadsNano", NanoThread.activeNanoThreads())
            .putR("threadsActive", NanoThread.activeCarrierThreads())
            .putR("otherThreads", ManagementFactory.getThreadMXBean().getThreadCount() - NanoThread.activeCarrierThreads())
            .putR("totalEvents", totalEvents.get())
            .putR("lastLogsRetained", logHistory.size())
            .putR("lastEventsRetained", eventHistory.size())
            .putR("lastUpdated", dateTimeFormatter.format(Instant.now()));

        loadInactiveServices().ifPresent(services -> systemInfo.putR("inactiveServices", services));
        return systemInfo;
    }

    protected static long getListenerCount(final Collection<Set<Consumer<? super Event<?, ?>>>> listenerList) {
        return new ArrayList<>(listenerList).stream().mapToLong(Collection::size).sum();
    }

    protected List<Service> getFilteredServices() {
        return context.services().stream().filter(svc -> !excludedServices.contains(svc.name())).toList();
    }

    protected Optional<Set<String>> loadInactiveServices() {
        if (servicesIndex.isEmpty()) {
            return Optional.empty();
        }

        final Set<String> activeServices = getFilteredServices().stream().map(Service::name).collect(Collectors.toSet());
        final Set<String> inactiveServices = new LinkedHashSet<>(servicesIndex);
        inactiveServices.removeAll(activeServices);

        return inactiveServices.isEmpty() ? Optional.empty() : Optional.of(inactiveServices);
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
        context.unsubscribeEvent(EVENT_APP_HEARTBEAT, channelListener);
        eventListenerMap.forEach((ch, listener) -> context.unsubscribeEvent(ch, (Consumer) listener));
        eventListenerMap.clear();
        eventHistory.clear();
        logHistory.clear();
        context.info(() -> "[{}] stopped", name());
    }

    @Override
    public void onEvent(Event<?, ?> event) {}

    public void removeLastNElements(final Deque<?> deque, final int N) {
        lock.lock();
        for (int i = 0; i < N; i++) {
            deque.removeLast();
        }
        lock.unlock();
    }

    private Set<String> fetchCorrectPropFile(final List<URL> urls) {
        String serviceFqcn = Service.class.getCanonicalName();
        Set<String> services = new HashSet<>();
        int svcCnt = 0;

        for (URL url : urls) {
            Properties p = new Properties();
            try (InputStream in = url.openStream()) {
                p.load(in);
            } catch (IOException ex) {
                // Skip unreadable files but continue
                continue;
            }

            String nanoServiceImpl = p.getProperty(serviceFqcn);
            if (null == nanoServiceImpl || nanoServiceImpl.isBlank()) {
                continue;
            }

            Set<String> serviceNames = Arrays.stream(nanoServiceImpl.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

            int currCnt = serviceNames.size();

            if (currCnt > svcCnt) {
                services = serviceNames;
                svcCnt = currCnt;
            }
        }
        return services;
    }
}
