package com.adaptivefit.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class PerformanceTiming {

    private static final Logger log = LoggerFactory.getLogger(PerformanceTiming.class);
    private static final String USER_HASH_SALT = UUID.randomUUID().toString();
    private static final ConcurrentHashMap<String, AtomicInteger> ACTIVE_USER_ENDPOINTS = new ConcurrentHashMap<>();
    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

    private PerformanceTiming() {}

    public static void beginRequest(String requestId, String endpoint) {
        CONTEXT.set(new RequestContext(requestId, endpoint));
        log.info("perf event=request.start requestId={} endpoint={} timestamp={}",
                requestId, endpoint, Instant.now());
    }

    public static void endRequest(int status) {
        RequestContext context = CONTEXT.get();
        if (context == null) return;
        try {
            long elapsedMs = elapsedMs(context.startedNanos);
            log.info("perf event=request.end requestId={} endpoint={} userRef={} status={} timestamp={} elapsedMs={}",
                    context.requestId, context.endpoint, valueOrUnknown(context.userRef), status, Instant.now(), elapsedMs);
        } finally {
            if (context.activeUserEndpointKey != null) {
                ACTIVE_USER_ENDPOINTS.computeIfPresent(context.activeUserEndpointKey, (key, count) ->
                        count.decrementAndGet() <= 0 ? null : count);
            }
            CONTEXT.remove();
        }
    }

    public static void setUserId(Long userId) {
        RequestContext context = CONTEXT.get();
        if (context == null || userId == null || context.userRef != null) return;
        context.userRef = hashUserId(userId);
        context.activeUserEndpointKey = context.endpoint + "|" + context.userRef;
        int concurrent = ACTIVE_USER_ENDPOINTS.computeIfAbsent(context.activeUserEndpointKey,
                ignored -> new AtomicInteger()).incrementAndGet();
        log.info("perf event=request.concurrency requestId={} endpoint={} userRef={} duplicate={}",
                context.requestId, context.endpoint, context.userRef, concurrent > 1);
    }

    public static String currentRequestId() {
        RequestContext context = CONTEXT.get();
        return context == null ? null : context.requestId;
    }

    public static <T> T measure(String stage, Supplier<T> operation) {
        RequestContext context = CONTEXT.get();
        if (context == null) return operation.get();

        long started = System.nanoTime();
        logStage(context, stage, "start", started, "status=running");
        try {
            T result = operation.get();
            logStage(context, stage, "end", started, "status=ok");
            return result;
        } catch (RuntimeException | Error failure) {
            logStage(context, stage, "end", started,
                    "status=error exception=" + failure.getClass().getSimpleName());
            throw failure;
        }
    }

    public static void event(String name, String details) {
        RequestContext context = CONTEXT.get();
        if (context == null) return;
        log.info("perf event={} requestId={} endpoint={} userRef={} timestamp={} {}",
                name, context.requestId, context.endpoint, valueOrUnknown(context.userRef), Instant.now(), details);
    }

    private static void logStage(RequestContext context, String stage, String phase, long started, String details) {
        log.info("perf event=stage.{} stage={} requestId={} endpoint={} userRef={} timestamp={} elapsedMs={} {}",
                phase, stage, context.requestId, context.endpoint, valueOrUnknown(context.userRef),
                Instant.now(), elapsedMs(started), details);
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private static String hashUserId(Long userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(USER_HASH_SALT.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(userId.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String valueOrUnknown(String value) {
        return value == null ? "unknown" : value;
    }

    private static final class RequestContext {
        private final String requestId;
        private final String endpoint;
        private final long startedNanos = System.nanoTime();
        private String userRef;
        private String activeUserEndpointKey;

        private RequestContext(String requestId, String endpoint) {
            this.requestId = requestId;
            this.endpoint = endpoint;
        }
    }
}
