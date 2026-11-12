package io.pokerbaby.web;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokerBabyWebTest {
    private PokerBabyWeb app;
    private HttpClient client;

    @BeforeEach
    void start() throws IOException {
        app = new PokerBabyWeb(0);
        client = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void stop() {
        client.close();
        app.close();
    }

    @Test
    void servesAllBundledAssetsAndDoesNotExposeArbitraryPaths() throws Exception {
        for (String path : new String[]{"/", "/app.js", "/style.css", "/favicon.svg"}) {
            HttpResponse<String> response = get(path);
            assertEquals(200, response.statusCode(), path);
            assertFalse(response.body().isBlank());
            assertTrue(response.headers().firstValue("Content-Security-Policy").isPresent());
        }
        assertEquals("text/html; charset=utf-8", get("/").headers().firstValue("Content-Type").orElseThrow());
        assertEquals(404, get("/build.gradle.kts").statusCode());
    }

    @Test
    void keepsBrowserSessionsSeparateAndRefreshRetainsTheCurrentHand() throws Exception {
        HttpResponse<String> first = get("/api/state");
        assertEquals(200, first.statusCode());
        assertTrue(first.headers().firstValue("Set-Cookie").orElseThrow().contains("HttpOnly; SameSite=Strict"));
        assertTrue(first.body().contains("\"opponentCards\":[]"));
        assertEquals(200, post("/api/action", "version=0&type=FOLD").statusCode());
        assertTrue(get("/api/state").body().contains("\"complete\":true"));
        try (HttpClient other = HttpClient.newHttpClient()) {
            HttpResponse<String> separate = other.send(HttpRequest.newBuilder(uri("/api/state")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertTrue(separate.body().contains("\"complete\":false"));
            assertTrue(separate.body().contains("\"version\":0"));
        }
        assertTrue(get("/api/state").body().contains("\"version\":1"));
    }

    @Test
    void rejectsIllegalStaleAndCrossOriginCommands() throws Exception {
        get("/api/state");
        assertEquals(400, post("/api/action", "version=0&type=RAISE&to=199").statusCode());
        assertEquals(400, post("/api/action", "version=0&type=CHECK").statusCode());
        assertTrue(get("/api/state").body().contains("\"version\":0"));
        assertEquals(200, post("/api/action", "version=0&type=FOLD").statusCode());
        assertEquals(409, post("/api/action", "version=0&type=FOLD").statusCode());
        HttpRequest foreign = HttpRequest.newBuilder(uri("/api/new"))
                .header("Origin", "https://example.org")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("version=1")).build();
        assertEquals(403, client.send(foreign, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertTrue(get("/api/state").body().contains("\"version\":1"));
    }

    @Test
    void enforcesMethodsContentTypesAndBodyLimits() throws Exception {
        assertEquals(405, get("/api/action").statusCode());
        assertEquals(405, post("/api/state", "").statusCode());
        assertEquals(404, get("/api/missing").statusCode());
        assertEquals(413, post("/api/new", "a=" + "x".repeat(4096)).statusCode());
        HttpRequest wrongType = HttpRequest.newBuilder(uri("/api/new"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertEquals(415, client.send(wrongType, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + app.port() + path);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
