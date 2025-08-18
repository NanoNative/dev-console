/*package org.ab.devconsole;

import org.ab.devconsole.service.DevConsoleService;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpServer;
import org.nanonative.nano.services.metric.logic.MetricService;

import java.util.Map;

import static org.nanonative.nano.services.http.HttpServer.CONFIG_SERVICE_HTTP_PORT;
import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_FORMATTER;
import static org.nanonative.nano.services.logging.LogService.CONFIG_LOG_LEVEL;
import static org.nanonative.nano.services.logging.model.LogLevel.DEBUG;

public class Main {


    public static void main(String[] args) {
        final Nano nano = new Nano(Map.of(
                CONFIG_LOG_LEVEL, DEBUG,
                CONFIG_LOG_FORMATTER, "console",
                CONFIG_SERVICE_HTTP_PORT, "8080"
        ), new MetricService(), new HttpServer(), new DevConsoleService());
    }
}*/
