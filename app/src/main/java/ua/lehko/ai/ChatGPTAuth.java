package ua.lehko.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Experimental open-source Sign in with ChatGPT implementation.
 * No client secret, API key, JWT, or sensitive user data is included in this repository.
 * OAuth tokens are only ever handled natively and are encrypted with Android Keystore.
 * Test on a real Android device before treating the integration as production-ready.
 */
public final class ChatGPTAuth {
    private static final String AUTH = "https://auth.openai.com/api/accounts/authorize";
    private static final String TOKEN = "https://auth.openai.com/api/accounts/oauth/token";
    private static final String JWKS = "https://auth.openai.com/.well-known/jwks.json";
    private static final String RESOURCE = "https://api.openai.com/v1";
    private static final String SCOPE =
            "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";
    private static final String KEY_ALIAS = "lehko_chatgpt_tokens";
    private final Context context;
    private final SharedPreferences prefs;
    private final SecureRandom random = new SecureRandom();
    private volatile String lastError = "";

    public interface BrowserOpener { void open(String url); }
    public ChatGPTAuth(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences("lehko_oauth", Context.MODE_PRIVATE);
    }
    public synchronized JSONObject status() {
        JSONObject result = new JSONObject();
        try {
            JSONObject profile = readCredentials();
            boolean connected = profile != null
                    && (" " + profile.optString("scope") + " ").contains(" chatgpt.tokens.use.direct ");
            result.put("connected", connected);
            result.put("email", connected ? profile.optString("email", "") : "");
            result.put("error", lastError);
        } catch (Exception ignored) {
            try { result.put("connected", false).put("error", "Не вдалося відкрити захищені дані."); }
            catch (Exception ignored2) {}
        }
        return result;
    }
    public synchronized void signOut() {
        prefs.edit().remove("credentials").apply();
        lastError = "";
    }
    public synchronized void signIn(BrowserOpener browser) throws Exception {
        lastError = "";
        JSONObject previous = readCredentials();
        String oldClientId = previous == null ? "" : previous.optString("client_id");
        String hostId = prefs.getString("host_id", null);
        if (hostId == null) {
            hostId = "urn:uuid:" + UUID.randomUUID().toString();
            prefs.edit().putString("host_id", hostId).commit();
        }
        String state = randomUrl(32), nonce = randomUrl(32), verifier = randomUrl(48);
        String challenge = b64(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        // Only the loopback listener accepts the authorization callback. Never use a WebView login.
        try (ServerSocket server = new ServerSocket(0, 1,
                InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(300000);
            String redirect = "http://127.0.0.1:" + server.getLocalPort() + "/auth/callback";
            Map<String,String> params = new LinkedHashMap<>();
            params.put("client_id", oldClientId.isEmpty() ? "dynamic_agent_client" : oldClientId);
            if (oldClientId.isEmpty()) params.put("agent_name_hint", "Lehko AI");
            if (previous != null && !previous.optString("id_token").isEmpty())
                params.put("id_token_hint", previous.optString("id_token"));
            params.put("ext_agent_host_id", hostId);
            params.put("response_type", "code");
            params.put("redirect_uri", redirect);
            params.put("scope", SCOPE);
            params.put("resource", RESOURCE);
            params.put("state", state);
            params.put("nonce", nonce);
            params.put("code_challenge_method", "S256");
            params.put("code_challenge", challenge);
            browser.open(AUTH + "?" + query(params));

            Map<String,String> callback;
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(12000);
                BufferedReader input = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.UTF_8));
                String request = input.readLine();
                if (request == null || !request.startsWith("GET /auth/callback?"))
                    throw new SecurityException("Неправильний шлях OAuth-callback.");
                String path = request.split(" ", 3)[1];
                Uri uri = Uri.parse("http://127.0.0.1" + path);
                callback = new HashMap<>();
                for (String name: uri.getQueryParameterNames())
                    callback.put(name, uri.getQueryParameter(name));
                String body = "<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width'>"
                    + "<title>Легко AI</title><div style='font:20px system-ui;padding:40px'>"
                    + "Поверніться до застосунку «Легко AI».</div>";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                OutputStream output = socket.getOutputStream();
                output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                     + "Cache-Control: no-store\r\nContent-Length: " + bytes.length
                     + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(bytes);
                output.flush();
            }
            if (!MessageDigest.isEqual(state.getBytes(StandardCharsets.US_ASCII),
                    callback.getOrDefault("state", "").getBytes(StandardCharsets.US_ASCII)))
                throw new SecurityException("Неправильний OAuth state.");
            if (callback.containsKey("error"))
                throw new IllegalStateException("Вхід не підтверджено: " + callback.get("error"));
            String issued = callback.getOrDefault("client_id", oldClientId);
            if (issued.isEmpty() || "dynamic_agent_client".equals(issued))
                throw new SecurityException("OpenAI не повернув client_id.");
            if (!oldClientId.isEmpty() && !oldClientId.equals(issued))
                throw new SecurityException("Невідповідність OAuth client_id.");
            String code = callback.get("code");
            if (code == null || code.isEmpty()) throw new SecurityException("Немає OAuth-коду.");
            Map<String,String> form = new LinkedHashMap<>();
            form.put("grant_type", "authorization_code");
            form.put("client_id", issued);
            form.put("code", code);
            form.put("code_verifier", verifier);
            form.put("redirect_uri", redirect);
            form.put("resource", RESOURCE);
            JSONObject received = requestJson("POST", TOKEN,
                    "application/x-www-form-urlencoded", query(form), null);
            JSONObject identity = verifyIdToken(received.optString("id_token"), issued, nonce);
            String subject = identity.getString("sub");
            if (previous != null && !previous.optString("subject").equals(subject))
                throw new SecurityException("Під час входу вибрано інший ChatGPT-акаунт.");
            String scope = received.optString("scope");
            if (!(" " + scope + " ").contains(" chatgpt.tokens.use.direct "))
                throw new SecurityException("Потрібен дозвіл на використання підписки ChatGPT.");
            JSONObject credentials = new JSONObject();
            credentials.put("client_id", issued).put("subject", subject)
                .put("email", identity.optString("email", ""))
                .put("id_token", received.getString("id_token"))
                .put("access_token", received.getString("access_token"))
                .put("refresh_token", received.optString("refresh_token", ""))
                .put("scope", scope)
                .put("expires_at", System.currentTimeMillis() / 1000 + received.optLong("expires_in", 3600));
            storeCredentials(credentials);
            lastError = "";
        } catch (Exception e) {
            lastError = "Вхід ChatGPT: " + safeMessage(e);
            throw e;
        }
    }

    private synchronized String accessToken() throws Exception {
        JSONObject c = readCredentials();
        if (c == null || !(" " + c.optString("scope") + " ").contains(" chatgpt.tokens.use.direct "))
            throw new IllegalStateException("Спочатку підключи ChatGPT Plus.");
        if (c.optLong("expires_at", 0) > System.currentTimeMillis() / 1000 + 120)
            return c.getString("access_token");
        String refresh = c.optString("refresh_token");
        if (refresh.isEmpty()) throw new IllegalStateException("Потрібно повторно увійти в ChatGPT.");
        Map<String,String> body = new LinkedHashMap<>();
        body.put("grant_type", "refresh_token");
        body.put("client_id", c.getString("client_id"));
        body.put("refresh_token", refresh);
        body.put("resource", RESOURCE);
        JSONObject updated = requestJson("POST", TOKEN,
                "application/x-www-form-urlencoded", query(body), null);
        c.put("access_token", updated.getString("access_token"));
        c.put("refresh_token", updated.optString("refresh_token", refresh));
        c.put("expires_at", System.currentTimeMillis()/1000 + updated.optLong("expires_in", 3600));
        if (updated.has("scope")) {
            String scope = updated.getString("scope");
            if (!(" " + scope + " ").contains(" chatgpt.tokens.use.direct "))
                throw new SecurityException("ChatGPT відкликав дозвіл на AI-запити.");
            c.put("scope", scope);
        }
        storeCredentials(c);
        return c.getString("access_token");
    }

    public String respond(String text, String base64Image) throws Exception {
        String bearer = accessToken();
        JSONObject catalog = requestJson("GET", RESOURCE + "/models", null, null, bearer);
        JSONArray models = catalog.optJSONArray("models");
        if (models == null || models.length() == 0)
            throw new IllegalStateException("У цьому ChatGPT-акаунті немає доступних моделей.");
        String model = null;
        // Prefer a model explicitly listed by the connected account; no hard-coded entitlement assumptions.
        for (int i=0; i<models.length(); i++) {
            JSONObject candidate = models.optJSONObject(i);
            if (candidate != null && candidate.optString("visibility").equals("list")) {
                model = candidate.optString("slug");
                if (!model.isEmpty()) break;
            }
        }
        if (model == null || model.isEmpty())
            throw new IllegalStateException("У списку немає придатної моделі.");
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "input_text").put("text", text));
        if (base64Image != null && !base64Image.isEmpty()) {
            if (!base64Image.startsWith("data:image/jpeg;base64,")
                    && !base64Image.startsWith("data:image/png;base64,"))
                throw new IllegalArgumentException("Непідтримуваний формат фото.");
            content.put(new JSONObject().put("type", "input_image")
                    .put("image_url", base64Image));
        }
        JSONObject body = new JSONObject();
        body.put("model", model).put("store", false).put("stream", true)
            .put("input", new JSONArray().put(new JSONObject().put("role", "user")
                .put("content", content)));
        HttpURLConnection conn = (HttpURLConnection) new URL(RESOURCE + "/responses").openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(90000);
        conn.setRequestProperty("Authorization", "Bearer " + bearer);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        byte[] outgoing = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = conn.getOutputStream()) { out.write(outgoing); }
        int code = conn.getResponseCode();
        if (code >= 400) {
            String error = readAll(conn.getErrorStream());
            throw new IOException("OpenAI " + code + ": " + error.substring(0, Math.min(320, error.length())));
        }
        StringBuilder result = new StringBuilder();
        boolean complete = false;
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                    conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String payload = line.substring(6);
                if ("[DONE]".equals(payload)) continue;
                JSONObject event;
                try { event = new JSONObject(payload); } catch (Exception ignored) { continue; }
                String type = event.optString("type");
                if ("response.output_text.delta".equals(type)) {
                    result.append(event.optString("delta"));
                    if (result.length() > 80000) throw new IOException("Занадто довга відповідь.");
                } else if ("response.completed".equals(type)) complete = true;
                else if ("response.failed".equals(type) || "response.incomplete".equals(type))
                    throw new IOException("Запит не завершено: " + payload.substring(0, Math.min(260, payload.length())));
            }
        } finally { conn.disconnect(); }
        if (!complete || result.length() == 0)
            throw new IOException("OpenAI не завершив відповідь. Перевір ліміти ChatGPT.");
        return result.toString();
    }

    private JSONObject verifyIdToken(String jwt, String clientId, String nonce) throws Exception {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) throw new SecurityException("Неправильний ID token.");
        JSONObject head = new JSONObject(new String(unb64(parts[0]), StandardCharsets.UTF_8));
        JSONObject claims = new JSONObject(new String(unb64(parts[1]), StandardCharsets.UTF_8));
        if (!"RS256".equals(head.optString("alg"))) throw new SecurityException("Недозволений JWT alg.");
        JSONObject jwks = requestJson("GET", JWKS, null, null, null);
        JSONArray keys = jwks.getJSONArray("keys");
        boolean verified = false;
        for (int i=0; i<keys.length(); i++) {
            JSONObject k = keys.optJSONObject(i);
            if (k == null || !"RSA".equals(k.optString("kty"))
                    || !k.optString("kid").equals(head.optString("kid"))) continue;
            RSAPublicKeySpec spec = new RSAPublicKeySpec(
                    new BigInteger(1, unb64(k.getString("n"))),
                    new BigInteger(1, unb64(k.getString("e"))));
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            verified = verifier.verify(unb64(parts[2]));
            break;
        }
        if (!verified) throw new SecurityException("Неправильний підпис ID token.");
        if (!"https://auth.openai.com".equals(claims.optString("iss"))
                || !nonce.equals(claims.optString("nonce"))
                || claims.optLong("exp") <= System.currentTimeMillis()/1000
                || claims.optString("sub").isEmpty())
            throw new SecurityException("Неправильні claims ID token.");
        Object aud = claims.opt("aud");
        boolean allowed = clientId.equals(aud);
        if (aud instanceof JSONArray) {
            JSONArray a = (JSONArray)aud;
            for (int i=0; i<a.length(); i++) if (clientId.equals(a.optString(i))) allowed = true;
        }
        if (!allowed) throw new SecurityException("Неправильний ID token audience.");
        return claims;
    }

    private JSONObject requestJson(String method, String url, String contentType,
            String body, String bearer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", contentType);
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = c.getResponseCode();
        String response = readAll(status >= 400 ? c.getErrorStream() : c.getInputStream());
        c.disconnect();
        if (status >= 400) throw new IOException("HTTP " + status + ": "
                + response.substring(0, Math.min(230, response.length())));
        return new JSONObject(response);
    }

    private String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream in=stream; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096]; int n;
            while ((n=in.read(buf)) >= 0) {
                if (out.size() > 1_000_000) throw new IOException("Надто велика відповідь.");
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }
    private static String query(Map<String,String> parameters) throws Exception {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String,String> p: parameters.entrySet()) {
            if (b.length() > 0) b.append("&");
            b.append(URLEncoder.encode(p.getKey(), "UTF-8")).append("=")
             .append(URLEncoder.encode(p.getValue(), "UTF-8"));
        }
        return b.toString();
    }
    private String randomUrl(int length) {
        byte[] v = new byte[length]; random.nextBytes(v); return b64(v);
    }
    private static String b64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
    private static byte[] unb64(String value) {
        return Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP);
    }
    private static byte[] sha256(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }
    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        KeyStore.Entry existing = ks.getEntry(KEY_ALIAS, null);
        if (existing instanceof KeyStore.SecretKeyEntry)
            return ((KeyStore.SecretKeyEntry)existing).getSecretKey();
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return gen.generateKey();
    }
    private synchronized void storeCredentials(JSONObject c) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] plain = c.toString().getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = cipher.doFinal(plain), iv = cipher.getIV();
        String record = Base64.encodeToString(iv, Base64.NO_WRAP) + ":"
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        if (!prefs.edit().putString("credentials", record).commit())
            throw new IOException("Не вдалося зберегти токен.");
    }
    private synchronized JSONObject readCredentials() throws Exception {
        String record = prefs.getString("credentials", null);
        if (record == null) return null;
        String[] p = record.split(":", 2);
        if (p.length != 2) throw new SecurityException("Зіпсовано збережені дані входу.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(
                128, Base64.decode(p[0], Base64.DEFAULT)));
        byte[] value = cipher.doFinal(Base64.decode(p[1], Base64.DEFAULT));
        return new JSONObject(new String(value, StandardCharsets.UTF_8));
    }
    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) return "невідома помилка";
        return message.substring(0, Math.min(message.length(), 180));
    }
}
