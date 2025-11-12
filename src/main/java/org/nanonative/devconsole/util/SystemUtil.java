package org.nanonative.devconsole.util;

import org.nanonative.nano.services.http.HttpServer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class SystemUtil {

    private static final AtomicLong LAST_WALL_NS = new AtomicLong(0L);
    private static final AtomicLong LAST_PROC_NS = new AtomicLong(0L);
    private static final AtomicReference<Double> LAST_PCT = new AtomicReference<>(0.0);

    private SystemUtil() {}

    public static String computeBaseUrl(final HttpServer http) {
        if (null == http)
            return "";

        final InetSocketAddress addr = http.address();
        if (null == addr)
            return "";

        final int port = http.port();
        String host;
        final InetAddress ia = addr.getAddress();
        final boolean isHttps = (http.server() instanceof com.sun.net.httpserver.HttpsServer);
        final boolean isDefaultPort = (isHttps && 443 == port) || (!isHttps && 80 == port);
        final String protocol = isHttps ? "https" : "http";

        if (null == ia || ia.isAnyLocalAddress()) {
            host = "localhost";
        } else {
            host = ia.getHostAddress();
        }

        return protocol + "://" + host + (isDefaultPort ? "" : ":" + port);
    }

    public static Double getCpuUsagePercent() {
        Duration cpuDur = ProcessHandle.current().info().totalCpuDuration().orElse(null);
        if (null == cpuDur)
            return LAST_PCT.get();

        long nowWall = System.nanoTime();
        long nowProc = cpuDur.toNanos();

        long prevWall = LAST_WALL_NS.getAndSet(nowWall);
        long prevProc = LAST_PROC_NS.getAndSet(nowProc);

        long dWall = nowWall - prevWall;
        long dProc = nowProc - prevProc;

        if (dWall <= 0 || dProc < 0)
            return LAST_PCT.get();

        int cores = Runtime.getRuntime().availableProcessors();
        double pct = (((double) dProc / (double) dWall) / cores) * 100.0;

        LAST_PCT.getAndSet(BigDecimal.valueOf(pct).setScale(2, RoundingMode.HALF_UP).doubleValue());
        return LAST_PCT.get();
    }
}
