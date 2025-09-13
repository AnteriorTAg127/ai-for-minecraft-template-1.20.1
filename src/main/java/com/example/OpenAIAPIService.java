package com.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;

import java.util.concurrent.*;

public class OpenAIAPIService {
    private OpenAIAPIConfig config;
    private static final Logger LOGGER = AiForMinecraft.LOGGER;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final BlockingQueue<CompletableFuture<String>> requestQueue = new LinkedBlockingQueue<>();

    public OpenAIAPIService() {
        try {
            reloadConfig();
        } catch (IOException e) {
            LOGGER.error("Failed to initialize OpenAI API service", e);
        }
    }

    public CompletableFuture<String> callAPIAsync(String message) {
        CompletableFuture<String> future = new CompletableFuture<>();
        executorService.submit(() -> {
            try {
                String result = callAPISync(message);
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    String callAPISync(String message) throws IOException, InterruptedException {
        if (config == null || config.getApiUrl() == null || config.getApiKey() == null) {
            throw new IllegalStateException("OpenAI API configuration is not properly loaded");
        }

        if (config.shouldRemoveThinkTags()) {
            message = message.replaceAll("<think>.*?</think>", "");
        }
        
        message = convertMarkdownToMinecraftFormat(message);

        HttpURLConnection connection = (HttpURLConnection) new URL(config.getApiUrl()).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
        connection.setDoOutput(true);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Minecraft-AI-Mod/1.0");

        String requestBody = String.format(
            "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"max_tokens\":%d,\"temperature\":%f}",
            config.getModel(), message, config.getMaxTokens(), config.getTemperature());

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }

            JsonObject jsonResponse = new Gson().fromJson(response.toString(), JsonObject.class);
            String result = jsonResponse.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
                
            if (config.shouldShowModelName()) {
                result = "[" + config.getModel() + "] " + result;
            }
            
            return result;
        }
    }

    private String convertMarkdownToMinecraftFormat(String text) {
        // 转换Markdown到Minecraft格式代码
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", "§l$1§r"); // 粗体
        text = text.replaceAll("\\*(.*?)\\*", "§o$1§r"); // 斜体
        text = text.replaceAll("`(.*?)`", "§7$1§r"); // 代码
        text = text.replaceAll("# (.*?)\\n", "§6$1§r\\n"); // 标题1
        text = text.replaceAll("## (.*?)\\n", "§e$1§r\\n"); // 标题2
        text = text.replaceAll("### (.*?)\\n", "§a$1§r\\n"); // 标题3
        text = text.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "§9$1§r"); // 链接
        return text;
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    public void reloadConfig() throws IOException {
        config = loadConfig();
        LOGGER.info("OpenAI API configuration reloaded");
    }

    private OpenAIAPIConfig loadConfig() throws IOException {
        Path externalConfigPath = Paths.get("config/ai-for-minecraft.json");

        if (!Files.exists(externalConfigPath)) {
            try {
                Files.createDirectories(externalConfigPath.getParent());
                Map<String, Object> defaultConfig = new HashMap<>();
                defaultConfig.put("api_url", "https://api.openai.com/v1/chat/completions");
                defaultConfig.put("api_key", "");
                defaultConfig.put("model", "gpt-3.5-turbo");
                defaultConfig.put("max_tokens", 1000);
                defaultConfig.put("temperature", 0.7);
                defaultConfig.put("system_prompt", "注意！所有mc皆指的是minecraft游戏。你需要作为一个讲解员用最简单最朴素的方式为玩家提供关于minecraft游戏的帮助。你的所有回答需要使用中文。");
                defaultConfig.put("show_model_name", true);
                defaultConfig.put("process_think_tags", true);

                Files.write(externalConfigPath, new GsonBuilder().setPrettyPrinting().create().toJsonTree(defaultConfig).toString().getBytes());
                LOGGER.info("已创建默认配置文件: {}", externalConfigPath);
            } catch (Exception e) {
                LOGGER.error("Failed to create default config file", e);
            }
        }

        try {
            String configContent = new String(Files.readAllBytes(externalConfigPath));
            Map<String, Object> configMap = new Gson().fromJson(configContent, Map.class);

            configMap.putIfAbsent("api_url", "https://api.openai.com/v1/chat/completions");
            configMap.putIfAbsent("api_key", "");
            configMap.putIfAbsent("model", "gpt-3.5-turbo");
            configMap.putIfAbsent("max_tokens", 1000);
            configMap.putIfAbsent("temperature", 0.7);
            configMap.putIfAbsent("system_prompt", "You are a helpful AI assistant in Minecraft.");
            configMap.putIfAbsent("show_model_name", true);
            configMap.putIfAbsent("process_think_tags", true);

            return new GsonBuilder().setPrettyPrinting().create().fromJson(new Gson().toJson(configMap), OpenAIAPIConfig.class);
        } catch (Exception e) {
            LOGGER.error("Failed to load config file", e);
            throw e;
        }
    }

    public OpenAIAPIConfig getConfig() {
        return config;
    }
}