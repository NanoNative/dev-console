package org.nanonative.devconsole.service;

import berlin.yuna.typemap.model.TypeInfo;
import org.junit.jupiter.api.Test;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;
import org.nanonative.nano.services.http.model.ContentType;
import org.nanonative.nano.services.http.model.HttpMethod;
import org.nanonative.nano.services.http.model.HttpObject;
import org.nanonative.nano.services.metric.logic.MetricService;

import java.util.Map;

import static org.nanonative.devconsole.service.DevConsoleService.BASE_URL;
import static org.nanonative.devconsole.service.DevConsoleService.CONFIG_DEV_CONSOLE_URL;
import static org.nanonative.devconsole.service.DevConsoleService.DEFAULT_MAX_EVENTS;
import static org.nanonative.devconsole.service.DevConsoleService.DEFAULT_MAX_LOGS;
import static org.nanonative.devconsole.service.DevConsoleService.DEFAULT_UI_URL;
import static org.nanonative.devconsole.service.DevConsoleService.DEV_CONFIG_URL;
import static org.nanonative.devconsole.service.DevConsoleService.DEV_EVENTS_URL;
import static org.nanonative.devconsole.service.DevConsoleService.DEV_INFO_URL;
import static org.nanonative.devconsole.service.DevConsoleService.DEV_LOGS_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.nanonative.devconsole.service.DevConsoleService.DEV_SERVICE_URL;
import static org.nanonative.devconsole.util.UiHelper.STATIC_FILES;

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
        assertThat(nano.stop(DevConsoleServiceTest.class).waitForStop().isReady()).isFalse();
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
        assertThat(nano.stop(DevConsoleServiceTest.class).waitForStop().isReady()).isFalse();
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
        assertThat(nano.stop(DevConsoleServiceTest.class).waitForStop().isReady()).isFalse();
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
        assertThat(nano.stop(DevConsoleServiceTest.class).waitForStop().isReady()).isFalse();
    }

    @Test
    void updateConfigTest() {
        final String newBaseUrl = "/tests";
        final int newMaxLogs = 1;
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

        final HttpObject checkNewUrl = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + newBaseUrl)
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(checkNewUrl.statusCode()).isEqualTo(200);
        assertThat(checkNewUrl.hasContentType(ContentType.TEXT_HTML));
        assertThat(checkNewUrl.bodyAsString()).contains("<!DOCTYPE html>");

        assertThat(devConsoleService.basePath).isEqualTo(newBaseUrl);
        assertThat(devConsoleService.maxLogs).isEqualTo(newMaxLogs);
        assertThat(devConsoleService.maxEvents).isEqualTo(DEFAULT_MAX_EVENTS);
        assertThat(nano.stop(DevConsoleServiceTest.class).waitForStop().isReady()).isFalse();
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
        assertThat(result.bodyAsString()).contains("<!DOCTYPE html>");
        assertThat(nano.stop(DevConsoleServiceTest.class).waitForStop().isReady()).isFalse();
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
        assertThat(STATIC_FILES.size()).isEqualTo(5);
        assertThat(result.bodyAsString()).contains("<!DOCTYPE html>");
        assertThat(nano.stop(DevConsoleServiceTest.class).waitForStop().isReady()).isFalse();
    }

    @Test
    void fetchJsTest() {
        final Nano nano = new Nano(new HttpServer(), new DevConsoleService(), new HttpClient());
        final HttpObject result = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + "/script.js")
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.hasContentType(ContentType.APPLICATION_JAVASCRIPT));
        assertThat(result.bodyAsString()).contains("document.addEventListener(\"DOMContentLoaded\"");
        assertThat(nano.stop(DevConsoleServiceTest.class).waitForStop().isReady()).isFalse();
    }

    @Test
    void fetchCssTest() {
        final Nano nano = new Nano(new HttpServer(), new DevConsoleService(), new HttpClient());
        final HttpObject result = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + "/style.css")
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.hasContentType(ContentType.TEXT_CSS));
        assertThat(result.bodyAsString()).contains("background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 50%, #cbd5e1 100%);");
        assertThat(nano.stop(DevConsoleServiceTest.class).waitForStop().isReady()).isFalse();
    }

    @Test
    void serviceDeregisterSuccessTest() throws InterruptedException {
        DevConsoleService devConsole = new DevConsoleService();
        final Nano nano = new Nano(new HttpServer(), devConsole, new HttpClient());
        final HttpObject beforeTestResult = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_INFO_URL)
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(beforeTestResult.statusCode()).isEqualTo(200);
        assertThat(beforeTestResult.hasContentType(ContentType.APPLICATION_JSON));
        final TypeInfo<?> responseBody = beforeTestResult.bodyAsJson();
        final long serviceCountBefore = responseBody.get(Integer.class, "runningServices");
        assertThat(serviceCountBefore).isEqualTo(1L);

        final HttpObject deregisterResult = new HttpObject()
            .methodType(HttpMethod.DELETE)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_SERVICE_URL + "/DevConsoleService")
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(deregisterResult.statusCode()).isEqualTo(200);

        // Some sleep to let the service shutdown
        Thread.sleep(2);

        final HttpObject afterTestResult = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_INFO_URL)
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(afterTestResult.statusCode()).isEqualTo(404);
        assertThat(nano.stop(DevConsoleServiceTest.class).waitForStop().isReady()).isFalse();
    }

    @Test
    void serviceDeregisterFailureTest() throws InterruptedException {
        DevConsoleService devConsole = new DevConsoleService();
        final Nano nano = new Nano(new HttpServer(), devConsole, new HttpClient(), new MetricService());
        final HttpObject beforeTestResult = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_INFO_URL)
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(beforeTestResult.statusCode()).isEqualTo(200);
        assertThat(beforeTestResult.hasContentType(ContentType.APPLICATION_JSON));
        final TypeInfo<?> responseBody = beforeTestResult.bodyAsJson();
        final long serviceCountBefore = devConsole.getFilteredServices().size();
        assertThat(responseBody).hasFieldOrPropertyWithValue("runningServices", serviceCountBefore);

        final HttpObject deregisterResult = new HttpObject()
            .methodType(HttpMethod.DELETE)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_SERVICE_URL + "/MetricService")
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(deregisterResult.statusCode()).isEqualTo(200);

        // Some sleep to let the service shutdown
        Thread.sleep(2);

        final long serviceCountAfter = devConsole.getFilteredServices().size();
        assertThat(serviceCountAfter).isEqualTo(serviceCountBefore - 1);

        final HttpObject duplicateCallResult = new HttpObject()
            .methodType(HttpMethod.DELETE)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_SERVICE_URL + "/MetricService")
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(duplicateCallResult.statusCode()).isEqualTo(500);
        final long serviceCountAfterDuplicate = devConsole.getFilteredServices().size();
        assertThat(serviceCountAfterDuplicate).isEqualTo(serviceCountAfter);
        assertThat(nano.stop(DevConsoleServiceTest.class).waitForStop().isReady()).isFalse();
    }

    @Test
    void serviceRegisterSuccessTest() throws InterruptedException {
        DevConsoleService devConsole = new DevConsoleService();
        final Nano nano = new Nano(new HttpServer(), devConsole, new HttpClient());
        final HttpObject beforeTestResult = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_INFO_URL)
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(beforeTestResult.statusCode()).isEqualTo(200);
        assertThat(beforeTestResult.hasContentType(ContentType.APPLICATION_JSON));
        final TypeInfo<?> responseBody = beforeTestResult.bodyAsJson();
        final long serviceCountBefore = devConsole.getFilteredServices().size();
        assertThat(responseBody).hasFieldOrPropertyWithValue("runningServices", serviceCountBefore);

        final HttpObject deregisterResult = new HttpObject()
            .methodType(HttpMethod.PATCH)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_SERVICE_URL + "/MetricService")
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(deregisterResult.statusCode()).isEqualTo(200);

        // Some sleep to let the service start
        Thread.sleep(2);

        final long serviceCountAfter = devConsole.getFilteredServices().size();
        assertThat(serviceCountAfter).isEqualTo(serviceCountBefore + 1);
        assertThat(nano.stop(DevConsoleServiceTest.class).waitForStop().isReady()).isFalse();
    }

    @Test
    void serviceRegisterFailureTest() throws InterruptedException {
        DevConsoleService devConsole = new DevConsoleService();
        final Nano nano = new Nano(new HttpServer(), devConsole, new HttpClient());
        final HttpObject beforeTestResult = new HttpObject()
            .methodType(HttpMethod.GET)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_INFO_URL)
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(beforeTestResult.statusCode()).isEqualTo(200);
        assertThat(beforeTestResult.hasContentType(ContentType.APPLICATION_JSON));
        final TypeInfo<?> responseBody = beforeTestResult.bodyAsJson();
        final long serviceCountBefore = devConsole.getFilteredServices().size();
        assertThat(responseBody).hasFieldOrPropertyWithValue("runningServices", serviceCountBefore);

        final HttpObject deregisterResult = new HttpObject()
            .methodType(HttpMethod.PATCH)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_SERVICE_URL + "/MetricService")
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(deregisterResult.statusCode()).isEqualTo(200);

        // Some sleep to let the service start
        Thread.sleep(2);

        final long serviceCountAfter = devConsole.getFilteredServices().size();
        assertThat(serviceCountAfter).isEqualTo(serviceCountBefore + 1);

        final HttpObject duplicateCallResult = new HttpObject()
            .methodType(HttpMethod.PATCH)
            .path(serverUrl + nano.service(HttpServer.class).port() + BASE_URL + DEV_SERVICE_URL + "/MetricService")
            .send(nano.context(DevConsoleServiceTest.class));
        assertThat(duplicateCallResult.statusCode()).isEqualTo(500);
        final long serviceCountAfterDuplicate = devConsole.getFilteredServices().size();
        assertThat(serviceCountAfterDuplicate).isEqualTo(serviceCountAfter);
        assertThat(nano.stop(DevConsoleServiceTest.class).waitForStop().isReady()).isFalse();
    }
}
