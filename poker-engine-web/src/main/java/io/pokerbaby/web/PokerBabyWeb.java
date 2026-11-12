package io.pokerbaby.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.pokerbaby.engine.IllegalActionException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local HTTP entry point for the playable browser demo. */
public final class PokerBabyWeb implements AutoCloseable {
    private static final int MAX_BODY = 4096;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    PokerBabyWeb(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    /**
     * Starts the local table and waits until the process is stopped.
     * @param args optional port, default 8080
     * @throws IOException if the port cannot be opened
     * @throws InterruptedException if the main thread is interrupted
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: PokerBabyWeb [port]");
        }
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        PokerBabyWeb app = new PokerBabyWeb(port);
        Runtime.getRuntime().addShutdownHook(new Thread(app::close));
        System.out.println("PokerBaby: http://localhost:" + app.port());
        new java.util.concurrent.CountDownLatch(1).await();
    }

    int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self'; "
                            + "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
            String path = exchange.getRequestURI().getPath();
            try {
                if (path.startsWith("/api/")) {
                    api(exchange, path);
                } else {
                    asset(exchange, path);
                }
            } catch (IllegalArgumentException | IllegalActionException exception) {
                json(exchange, 400, Map.of("error", "Недопустимое действие или размер ставки."));
            } catch (IllegalStateException exception) {
                json(exchange, 409, Map.of("error", exception.getMessage()));
            }
        }
    }

    private void api(HttpExchange exchange, String path) throws IOException {
        boolean reading = path.equals("/api/state") || path.equals("/api/replay");
        if (!reading && !Arrays.asList("/api/new", "/api/next", "/api/action", "/api/resume").contains(path)) {
            json(exchange, 404, Map.of("error", "Такой команды нет."));
            return;
        }
        String requiredMethod = reading ? "GET" : "POST";
        if (!exchange.getRequestMethod().equals(requiredMethod)) {
            exchange.getResponseHeaders().set("Allow", requiredMethod);
            json(exchange, 405, Map.of("error", "Метод не поддерживается."));
            return;
        }
        if (!reading && !sameOrigin(exchange)) {
            json(exchange, 403, Map.of("error", "Откройте стол в локальной вкладке."));
            return;
        }
        Map<String, String> fields = Map.of();
        if (!reading) {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.split(";", 2)[0].equalsIgnoreCase("application/x-www-form-urlencoded")) {
                json(exchange, 415, Map.of("error", "Ожидаются данные формы."));
                return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) {
                json(exchange, 413, Map.of("error", "Слишком большой запрос."));
                return;
            }
            fields = form(new String(body, StandardCharsets.UTF_8));
        }
        GameSession session = session(exchange);
        if (path.equals("/api/replay")) {
            Map<String, String> query = form(java.util.Objects.requireNonNullElse(exchange.getRequestURI().getRawQuery(), ""));
            json(exchange, 200, session.replay(Long.parseLong(query.getOrDefault("hand", ""))));
        } else {
            json(exchange, 200, reading ? session.state() : session.command(path.substring(5), fields));
        }
    }

    private GameSession session(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie != null) {
            for (String part : cookie.split(";")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length == 2 && pair[0].equals("pokerbaby")) {
                    GameSession existing = sessions.get(pair[1]);
                    if (existing != null) {
                        return existing;
                    }
                }
            }
        }
        String id = UUID.randomUUID().toString();
        GameSession created = new GameSession();
        sessions.put(id, created);
        exchange.getResponseHeaders().add("Set-Cookie", "pokerbaby=" + id + "; Path=/; HttpOnly; SameSite=Strict");
        return created;
    }

    private static boolean sameOrigin(HttpExchange exchange) {
        if ("cross-site".equals(exchange.getRequestHeaders().getFirst("Sec-Fetch-Site"))) {
            return false;
        }
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        return origin == null || origin.equals("http://" + exchange.getRequestHeaders().getFirst("Host"));
    }

    private static Map<String, String> form(String body) {
        Map<String, String> fields = new HashMap<>();
        for (String part : body.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2) {
                fields.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
        }
        return fields;
    }

    private static void asset(HttpExchange exchange, String path) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.getResponseHeaders().set("Allow", "GET");
            json(exchange, 405, Map.of("error", "Метод не поддерживается."));
            return;
        }
        String resource = switch (path) {
            case "/", "/index.html" -> "index.html";
            case "/app.js" -> "app.js";
            case "/style.css" -> "style.css";
            case "/favicon.svg" -> "favicon.svg";
            default -> null;
        };
        if (resource == null) {
            json(exchange, 404, Map.of("error", "Страница не найдена."));
            return;
        }
        String type = switch (resource.substring(resource.lastIndexOf('.'))) {
            case ".html" -> "text/html; charset=utf-8";
            case ".css" -> "text/css; charset=utf-8";
            case ".svg" -> "image/svg+xml";
            default -> "text/javascript; charset=utf-8";
        };
        try (var input = PokerBabyWeb.class.getResourceAsStream("/web/" + resource)) {
            if (input == null) {
                throw new IOException("Missing bundled resource: " + resource);
            }
            send(exchange, 200, type, input.readAllBytes());
        }
    }

    private static void json(HttpExchange exchange, int status, Object value) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", Json.encode(value).getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    /** Stops the HTTP listener and its request executor. */
    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }
}
