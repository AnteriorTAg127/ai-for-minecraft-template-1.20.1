package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class AiForMinecraft implements ModInitializer {
	public static final String MOD_ID = "ai-for-minecraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static AiForMinecraft instance;
	
	private final OpenAIAPIService openAIService = new OpenAIAPIService();
	private final AICommandHandler commandHandler = new AICommandHandler(openAIService);
	private OpenAIAPIConfig config;
	
	public static AiForMinecraft getInstance() {
		return instance;
	}

	@Override
	public void onInitialize() {
		instance = this;
		try {
			CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commandHandler.registerCommands(dispatcher, environment));
			LOGGER.info("AI for Minecraft mod initialized successfully");
		} catch (Exception e) {
			LOGGER.error("Failed to initialize AI for Minecraft mod", e);
		}
	}
	
	private OpenAIAPIConfig loadConfig() throws java.io.IOException {
		Path externalConfigPath = Paths.get("config/ai-for-minecraft.json");
		
		// 如果配置文件不存在，创建默认配置
		if (!Files.exists(externalConfigPath)) {
			try {
				Files.createDirectories(externalConfigPath.getParent());
				Map<String, Object> defaultConfig = new HashMap<>();
				defaultConfig.put("api_url", "https://api.openai.com/v1/chat/completions");
				defaultConfig.put("api_key", "");
				defaultConfig.put("model", "gpt-3.5-turbo");
				defaultConfig.put("max_tokens", 1000);
				defaultConfig.put("temperature", 0.7);
				defaultConfig.put("system_prompt", "You are a helpful AI assistant in Minecraft.");
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
			
			// 确保所有必需的配置项都存在
			configMap.putIfAbsent("api_url", "https://api.openai.com/v1/chat/completions");
			configMap.putIfAbsent("api_key", "");
			configMap.putIfAbsent("model", "gpt-3.5-turbo");
			configMap.putIfAbsent("max_tokens", 1000);
			configMap.putIfAbsent("temperature", 0.7);
			configMap.putIfAbsent("system_prompt", "You are a helpful AI assistant in Minecraft.");
			configMap.putIfAbsent("show_model_name", true);
			configMap.putIfAbsent("process_think_tags", true);
			
			// 转换为配置对象
			OpenAIAPIConfig config = new Gson().fromJson(new Gson().toJson(configMap), OpenAIAPIConfig.class);
			LOGGER.info("成功从外部配置文件加载OpenAI配置，API端点: {}，模型: {}", config.getApiUrl(), config.getModel());
			
			// 验证必要配置项
			if (config.getApiUrl() == null || config.getApiKey() == null) {
				LOGGER.error("缺少必要配置项: apiUrl 或 apiKey");
				throw new IllegalStateException("Missing required API configuration");
			}
			
			return config;
		} catch (Exception e) {
			LOGGER.error("Failed to load config file", e);
			throw e;
		}
	}
	
	private String callOpenAIAPI(String message) throws java.io.IOException, InterruptedException {
		if (config == null || config.getApiUrl() == null || config.getApiKey() == null) {
			throw new IllegalStateException("OpenAI API configuration is not properly loaded");
		}
		
		HttpURLConnection connection = (HttpURLConnection) new URL(config.getApiUrl()).openConnection();
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type", "application/json");
		connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
		connection.setDoOutput(true);
		
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
			return jsonResponse.getAsJsonArray("choices")
				.get(0).getAsJsonObject()
				.getAsJsonObject("message")
				.get("content").getAsString();
		}
	}
	
	// 新增配置重载方法
    void reloadConfig() throws java.io.IOException {
		config = loadConfig();
		LOGGER.info("OpenAI API configuration reloaded");
	}
	
	// 更新配置方法
	public void updateConfig(String option, String value) throws Exception {
		Path configPath = Paths.get("config/ai-for-minecraft.json");
		Map<String, Object> configMap = new Gson().fromJson(new String(Files.readAllBytes(configPath)), Map.class);
		
		switch (option.toLowerCase()) {
			case "system_prompt":
				configMap.put("system_prompt", value);
				break;
			case "show_model_name":
				configMap.put("show_model_name", Boolean.parseBoolean(value));
				break;
			case "process_think_tags":
				configMap.put("process_think_tags", Boolean.parseBoolean(value));
				break;
			default:
				throw new IllegalArgumentException("不支持的配置项: " + option);
		}
		
		Files.write(configPath, new GsonBuilder().setPrettyPrinting().create().toJson(configMap).toString().getBytes());
		reloadConfig();
		openAIService.reloadConfig();
	}
}