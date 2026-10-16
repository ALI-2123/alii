package dev.blade;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/** Friends, chat and cosmetic sync with the Blade server. Everything network-related runs off the game thread. */
public final class Social {
    public record Friend(String name, boolean online) {}

    public record Msg(String from, String to, String text) {}

    private static final Social INSTANCE = new Social();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(daemon("blade-social-actions"));
    private final List<Msg> global = new CopyOnWriteArrayList<>();
    private final Map<String, List<Msg>> dms = new ConcurrentHashMap<>();

    private volatile List<Friend> friends = List.of();
    private volatile List<String> requests = List.of();
    private volatile Map<String, Cosmetics.Set> others = Map.of();
    private volatile boolean connected;
    private volatile String error = "";
    private boolean started;

    private Social() {}

    public static Social get() {
        return INSTANCE;
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    public List<Friend> friends() { return friends; }
    public List<String> requests() { return requests; }
    public boolean connected() { return connected; }
    public String error() { return error; }
    public List<Msg> global() { return global; }

    public List<Msg> dm(String name) {
        return dms.getOrDefault(name.toLowerCase(), List.of());
    }

    public Cosmetics.Set cosmeticsOf(String name) {
        return others.get(name.toLowerCase());
    }

    public String me() {
        return Profile.serverName;
    }

    // ---------- connection ----------

    public synchronized void start() {
        if (started) return;
        started = true;
        if (Profile.serverUrl.isEmpty() || Profile.serverToken.isEmpty()) {
            error = "Social is off. Start the game from the Blade launcher with a Blade server set.";
            return;
        }
        Thread stream = new Thread(this::streamLoop, "blade-social-stream");
        stream.setDaemon(true);
        stream.start();
        Executors.newSingleThreadScheduledExecutor(daemon("blade-social-cosmetics"))
                .scheduleWithFixedDelay(this::refreshCosmetics, 4, 10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private HttpRequest.Builder request(String route) {
        return HttpRequest.newBuilder(URI.create(Profile.serverUrl + route))
                .timeout(Duration.ofSeconds(8))
                .header("x-name", Profile.serverName)
                .header("x-token", Profile.serverToken);
    }

    private JsonObject call(String method, String route, JsonObject body) throws IOException, InterruptedException {
        HttpRequest.Builder b = request(route).header("Content-Type", "application/json");
        if (body == null) b.method(method, HttpRequest.BodyPublishers.noBody());
        else b.method(method, HttpRequest.BodyPublishers.ofString(body.toString()));
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonObject json = new JsonObject();
        try {
            JsonElement parsed = JsonParser.parseString(res.body());
            if (parsed.isJsonObject()) json = parsed.getAsJsonObject();
        } catch (Exception ignored) {
            // leave json empty
        }
        if (res.statusCode() != 200) {
            throw new IOException(json.has("error") ? json.get("error").getAsString() : "Server error " + res.statusCode());
        }
        return json;
    }

    private void streamLoop() {
        while (true) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(Profile.serverUrl + "/events"))
                        .header("x-name", Profile.serverName)
                        .header("x-token", Profile.serverToken)
                        .GET()
                        .build();
                HttpResponse<Stream<String>> res = http.send(req, HttpResponse.BodyHandlers.ofLines());
                if (res.statusCode() != 200) throw new IOException("Server error " + res.statusCode());
                connected = true;
                error = "";
                refreshMe();
                postCosmeticsNow();
                try (Stream<String> lines = res.body()) {
                    lines.forEach(this::onLine);
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                error = "Can't reach the Blade server";
            }
            connected = false;
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void onLine(String line) {
        if (!line.startsWith("data: ")) return;
        try {
            handle(JsonParser.parseString(line.substring(6)).getAsJsonObject());
        } catch (Exception ignored) {
            // a broken event is skipped
        }
    }

    private void handle(JsonObject o) throws IOException, InterruptedException {
        String type = o.get("type").getAsString();
        switch (type) {
            case "history" -> {
                global.clear();
                for (JsonElement m : o.getAsJsonArray("messages")) global.add(msg(m.getAsJsonObject()));
            }
            case "friends" -> refreshMe();
            case "request" -> {
                refreshMe();
                notice(o.get("from").getAsString() + " sent you a friend request. Open the Blade menu to answer.");
            }
            case "chat" -> {
                Msg m = msg(o);
                if (m.to() == null) {
                    global.add(m);
                    if (global.size() > 100) global.remove(0);
                } else {
                    boolean mine = m.from().equalsIgnoreCase(me());
                    String other = mine ? m.to() : m.from();
                    dms.computeIfAbsent(other.toLowerCase(), k -> new CopyOnWriteArrayList<>()).add(m);
                    if (!mine) notice(m.from() + ": " + m.text());
                }
            }
            default -> { }
        }
    }

    private static Msg msg(JsonObject o) {
        return new Msg(o.get("from").getAsString(), o.has("to") ? o.get("to").getAsString() : null, o.get("text").getAsString());
    }

    private void notice(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[Blade] ").formatted(Formatting.RED).append(Text.literal(text).formatted(Formatting.WHITE)));
            }
        });
    }

    private void refreshMe() throws IOException, InterruptedException {
        JsonObject me = call("GET", "/me", null);
        List<Friend> list = new ArrayList<>();
        for (JsonElement f : me.getAsJsonArray("friends")) {
            JsonObject fo = f.getAsJsonObject();
            list.add(new Friend(fo.get("name").getAsString(), fo.get("online").getAsBoolean()));
        }
        List<String> reqs = new ArrayList<>();
        for (JsonElement r : me.getAsJsonArray("requests")) reqs.add(r.getAsString());
        friends = List.copyOf(list);
        requests = List.copyOf(reqs);
    }

    private void refreshCosmetics() {
        if (!connected) return;
        try {
            JsonObject all = call("GET", "/cosmetics", null);
            Map<String, Cosmetics.Set> next = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : all.entrySet()) {
                JsonObject c = e.getValue().getAsJsonObject();
                Cosmetics.Set s = new Cosmetics.Set();
                s.aura = c.get("aura").getAsString();
                s.wings = c.get("wings").getAsString();
                s.blade = c.get("blade").getAsString();
                s.trail = c.get("trail").getAsString();
                next.put(e.getKey().toLowerCase(), s);
            }
            others = Map.copyOf(next);
        } catch (Exception ignored) {
            // try again next time
        }
    }

    // ---------- actions (called from screens) ----------

    private void postCosmeticsNow() throws IOException, InterruptedException {
        Cosmetics.Set s = Profile.cosmetics();
        JsonObject body = new JsonObject();
        body.addProperty("aura", s.aura);
        body.addProperty("wings", s.wings);
        body.addProperty("blade", s.blade);
        body.addProperty("trail", s.trail);
        call("POST", "/cosmetics", body);
    }

    public void postCosmetics() {
        if (!connected) return;
        worker.execute(() -> {
            try {
                postCosmeticsNow();
            } catch (Exception ignored) {
                // the next reconnect sends it again
            }
        });
    }

    private void action(String route, String field, String value) {
        worker.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty(field, value);
                call("POST", route, body);
                refreshMe();
                error = "";
            } catch (Exception e) {
                error = e.getMessage() == null ? "Something went wrong" : e.getMessage();
            }
        });
    }

    public void addFriend(String name) { action("/friends/request", "to", name); }
    public void accept(String name) { action("/friends/accept", "from", name); }
    public void decline(String name) { action("/friends/decline", "from", name); }
    public void remove(String name) { action("/friends/remove", "name", name); }

    public void send(String text, String to) {
        worker.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("text", text);
                if (to != null) body.addProperty("to", to);
                call("POST", "/chat", body);
                error = "";
            } catch (Exception e) {
                error = e.getMessage() == null ? "Couldn't send" : e.getMessage();
            }
        });
    }
}
