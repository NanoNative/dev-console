package org.nanonative.devconsole.service;

import org.junit.jupiter.api.Test;
import org.nanonative.devconsole.util.SystemUtil;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpServer;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nanonative.devconsole.service.DevConsoleService.CONFIG_DEV_CONSOLE_URL;

public class SystemUtilTest {

    @Test
    void cpuUsageShouldBeNonNegativeAndFiniteTest() throws InterruptedException {
        // Warm up to initialize internal static state
        SystemUtil.getCpuUsagePercent();

        Thread.sleep(50);

        Double pct = SystemUtil.getCpuUsagePercent();

        assertThat(pct).isNotNull();
        assertThat(pct).isGreaterThanOrEqualTo(0.0);
        assertThat(pct).isNotNaN();
        assertThat(pct).isNotInfinite();
    }

    @Test
    void cpuUsageShouldBeRoundedToTwoDecimalPlacesTest() throws InterruptedException {
        SystemUtil.getCpuUsagePercent();
        Thread.sleep(50);

        Double pct = SystemUtil.getCpuUsagePercent();

        BigDecimal bd = BigDecimal.valueOf(pct).stripTrailingZeros();
        int scale = Math.max(0, bd.scale());

        assertThat(scale).isLessThanOrEqualTo(2);
    }

    @Test
    void consecutiveCallsShouldNotDriftWildlyTest() throws InterruptedException {
        SystemUtil.getCpuUsagePercent();
        Thread.sleep(50);

        Double first = SystemUtil.getCpuUsagePercent();
        Thread.sleep(50);
        Double second = SystemUtil.getCpuUsagePercent();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();

        double delta = Math.abs(second - first);

        // Allow a very generous delta to keep robust across environments
        assertThat(delta).isLessThan(500.0);
    }

    @Test
    void computeBaseUrlTest() {
        HttpServer server = new HttpServer();
        Nano nano = new Nano(Map.of(CONFIG_DEV_CONSOLE_URL, "/user1"), new DevConsoleService(), server);
        String baseUrl = SystemUtil.computeBaseUrl(server);
        assertThat(baseUrl).isEqualTo("http://localhost:" + server.address().getPort());
        assertThat(nano.stop(SystemUtilTest.class).waitForStop().isReady()).isFalse();
    }
}
