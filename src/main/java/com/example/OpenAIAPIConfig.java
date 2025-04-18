package com.example;

import com.google.gson.annotations.SerializedName;

public class OpenAIAPIConfig {
    @SerializedName("api_url")
    private String apiUrl;
    
    @SerializedName("api_key")
    private String apiKey;
    
    @SerializedName("model")
    private String model;
    
    @SerializedName("max_tokens")
    private int maxTokens;
    
    @SerializedName("temperature")
    private double temperature;
    
    @SerializedName("show_model_name")
    private boolean showModelName = true;
    
    @SerializedName("process_think_tags")
    private boolean processThinkTags = true;
    
    @SerializedName("system_prompt")
    private String systemPrompt = "You are a helpful AI assistant in Minecraft.";

    public String getApiUrl() {
        return apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }
    
    public boolean shouldShowModelName() {
        return showModelName;
    }
    
    public boolean shouldRemoveThinkTags() {
        return processThinkTags;
    }
    
    public String getSystemPrompt() {
        return systemPrompt;
    }
}