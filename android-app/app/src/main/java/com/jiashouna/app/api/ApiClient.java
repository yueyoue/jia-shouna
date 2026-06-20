package com.jiashouna.app.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jiashouna.app.App;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiClient {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private static final Gson gson = new Gson();

    public interface ApiCallback {
        void onSuccess(JsonObject data);
        void onError(String msg);
    }

    // GET 请求
    public static void get(String path, Map<String, String> params, ApiCallback callback) {
        StringBuilder url = new StringBuilder(App.BASE_URL + path);
        if (params != null && !params.isEmpty()) {
            url.append(url.toString().contains("?") ? "&" : "?");
            for (Map.Entry<String, String> e : params.entrySet()) {
                url.append(e.getKey()).append("=").append(e.getValue()).append("&");
            }
        }
        Request.Builder builder = new Request.Builder().url(url.toString()).get();
        String token = App.getInstance().getToken();
        if (!token.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
        client.newCall(builder.build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onError("网络错误: " + e.getMessage());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                handleResponse(response, callback);
            }
        });
    }

    // POST JSON 请求
    public static void post(String path, JsonObject body, ApiCallback callback) {
        String token = App.getInstance().getToken();
        Request.Builder builder = new Request.Builder()
                .url(App.BASE_URL + path)
                .post(RequestBody.create(body.toString(), JSON));
        if (!token.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
        client.newCall(builder.build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onError("网络错误: " + e.getMessage());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                handleResponse(response, callback);
            }
        });
    }

    // 同步GET
    public static JsonObject syncGet(String path, Map<String, String> params) throws IOException {
        StringBuilder url = new StringBuilder(App.BASE_URL + path);
        if (params != null && !params.isEmpty()) {
            url.append(url.toString().contains("?") ? "&" : "?");
            for (Map.Entry<String, String> e : params.entrySet()) {
                url.append(e.getKey()).append("=").append(e.getValue()).append("&");
            }
        }
        Request.Builder builder = new Request.Builder().url(url.toString()).get();
        String token = App.getInstance().getToken();
        if (!token.isEmpty()) builder.addHeader("Authorization", "Bearer " + token);
        Response response = client.newCall(builder.build()).execute();
        if (response.body() != null) {
            return JsonParser.parseString(response.body().string()).getAsJsonObject();
        }
        return null;
    }

    private static void handleResponse(Response response, ApiCallback callback) {
        try {
            if (response.body() != null) {
                String body = response.body().string();
                // 尝试提取JSON部分（跳过可能的PHP警告输出）
                int jsonStart = body.indexOf('{');
                if (jsonStart > 0) {
                    body = body.substring(jsonStart);
                }
                com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(body));
                reader.setLenient(true);
                JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                int code = json.get("code").getAsInt();
                if (code == 0) {
                    callback.onSuccess(json.has("data") && !json.get("data").isJsonNull() ? json.getAsJsonObject("data") : new JsonObject());
                } else {
                    callback.onError(json.has("msg") ? json.get("msg").getAsString() : "请求失败");
                }
            } else {
                callback.onError("响应为空");
            }
        } catch (Exception e) {
            callback.onError("解析错误: " + e.getMessage());
        }
    }
}
