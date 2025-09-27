package org.nanonative.ab.devconsole.service;

import org.junit.jupiter.api.Test;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;
import org.nanonative.nano.services.http.model.ContentType;
import org.nanonative.nano.services.http.model.HttpMethod;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.Map;

import static org.nanonative.ab.devconsole.service.DevConsoleService.BASE_URL;
import static org.nanonative.ab.devconsole.service.DevConsoleService.CONFIG_DEV_CONSOLE_URL;
import static org.nanonative.ab.devconsole.service.DevConsoleService.DEFAULT_MAX_EVENTS;
import static org.nanonative.ab.devconsole.service.DevConsoleService.DEFAULT_MAX_LOGS;
import static org.nanonative.ab.devconsole.service.DevConsoleService.DEFAULT_UI_URL;
import static org.nanonative.ab.devconsole.service.DevConsoleService.DEV_CONFIG_URL;
import static org.nanonative.ab.devconsole.service.DevConsoleService.DEV_EVENTS_URL;
import static org.nanonative.ab.devconsole.service.DevConsoleService.DEV_INFO_URL;
import static org.nanonative.ab.devconsole.service.DevConsoleService.DEV_LOGS_URL;
import static org.assertj.core.api.Assertions.assertThat;

class DevConsoleServiceTest {

    protected static String serverUrl = "http://localhost:";

    @Test
    void fetchEventsTest() {
        final Nano nano = new Nano(new HttpServer(), new DevConsoleService(), new HttpClient());
        final HttpObject result = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_EVENTS_URL)
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.hasContentType(ContentType.APPLICATION_JSON));
        assertThat(result.bodyAsString()).contains("channel").contains("payload").contains("response");
    }

    @Test
    void fetchSystemInfoTest() {
        final Nano nano = new Nano(new HttpServer(), new DevConsoleService(), new HttpClient());
        final HttpObject result = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_INFO_URL)
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.hasContentType(ContentType.APPLICATION_JSON));
        assertThat(result.bodyAsString()).contains("pid").contains("totalEvents");
    }

    @Test
    void fetchLogTest() {
        String log = "Test log output";
        final Nano nano = new Nano(new HttpServer(), new DevConsoleService(), new HttpClient());
        nano.context(DevConsoleServiceTest.class).info(() -> log);
        final HttpObject result = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_LOGS_URL)
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.hasContentType(ContentType.APPLICATION_JSON));
        assertThat(result.bodyAsString()).contains(log);
    }

    @Test
    void fetchConfigTest() {
        final Nano nano = new Nano(new HttpServer(), new DevConsoleService(), new HttpClient());
        final HttpObject result = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_CONFIG_URL)
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.hasContentType(ContentType.APPLICATION_JSON));
        assertThat(result.bodyAsString()).contains("maxEvents").contains("maxLogs").contains("baseUrl");
    }

    @Test
    void updateConfigTest() {
        final String newBaseUrl = "/tests";
        final Integer newMaxLogs = 101;
        final DevConsoleService devConsoleService = new DevConsoleService();
        final Nano nano = new Nano(new HttpServer(), devConsoleService, new HttpClient());
        final String baseUrl = devConsoleService.basePath;
        final Integer maxLogs = devConsoleService.maxLogs;

        final HttpObject result = new HttpObject()
            .methodType(HttpMethod.PATCH)
            .body(Map.of("baseUrl", newBaseUrl, "maxLogs", newMaxLogs))
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_CONFIG_URL)
            .send(nano.context(DevConsoleServiceTest.class));

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.hasContentType(ContentType.APPLICATION_JSON));
        assertThat(result.bodyAsString()).contains(newBaseUrl).contains(String.valueOf(newMaxLogs));
        assertThat(baseUrl).isEqualTo(DEFAULT_UI_URL);
        assertThat(maxLogs).isEqualTo(DEFAULT_MAX_LOGS);
        assertThat(devConsoleService.basePath).isEqualTo(newBaseUrl);
        assertThat(devConsoleService.maxLogs).isEqualTo(newMaxLogs);
        assertThat(devConsoleService.maxEvents).isEqualTo(DEFAULT_MAX_EVENTS);
    }

    @Test
    void fetchHtmlUsingDefaultUrlTest() {
        final Nano nano = new Nano(new HttpServer(), new DevConsoleService(), new HttpClient());
        final HttpObject result = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEFAULT_UI_URL)
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.hasContentType(ContentType.TEXT_HTML));
        assertThat(DevConsoleService.STATIC_FILES.size()).isEqualTo(4);
        assertThat(result.bodyAsString()).contains("<!DOCTYPE html>");
    }

    @Test
    void fetchHtmlUsingCustomUrlTest() {
        final String customPath = "/ab";
        final Nano nano = new Nano(Map.of(
            CONFIG_DEV_CONSOLE_URL, customPath
        ), new HttpServer(), new DevConsoleService(), new HttpClient());
        final HttpObject result = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + customPath)
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.hasContentType(ContentType.TEXT_HTML));
        assertThat(DevConsoleService.STATIC_FILES.size()).isEqualTo(4);
        assertThat(result.bodyAsString()).contains("<!DOCTYPE html>");
    }
}
