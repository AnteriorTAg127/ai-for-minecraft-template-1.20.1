package com.example;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.text.Text;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import org.slf4j.Logger;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

public class AICommandHandler {
    private final OpenAIAPIService openAIService;
    private static final Logger LOGGER = AiForMinecraft.LOGGER;

    public AICommandHandler(OpenAIAPIService openAIService) {
        this.openAIService = openAIService;
    }



public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
                             CommandManager.RegistrationEnvironment environment) {
        // 原有ai命令
        dispatcher.register(literal("ai")
            .then(argument("message", StringArgumentType.greedyString())
            .executes(context -> {
                ServerCommandSource source = context.getSource();
                String message = StringArgumentType.getString(context, "message");
                
                if (message == null || message.trim().isEmpty()) {
                    source.sendError(Text.literal("Message cannot be empty"));
                    return 0;
                }
                
                try {
                    openAIService.callAPIAsync(message).thenAccept(response -> {
                        // 分割长文本以避免全服广播，并确保只发送给命令执行者
                        int maxLength = 256;
                        for (int i = 0; i < response.length(); i += maxLength) {
                            String part = response.substring(i, Math.min(response.length(), i + maxLength));
                            String finalPart = part;
                            source.getPlayer().sendMessage(Text.literal(finalPart), false);
                        }
                    }).exceptionally(error -> {
                        LOGGER.error("Failed to call OpenAI API", error);
                        source.sendError(Text.literal("Failed to get AI response: " + error.getMessage()));
                        return null;
                    });
                    return 1;
                } catch (Exception e) {
                    LOGGER.error("Failed to call OpenAI API", e);
                    source.sendError(Text.literal("Failed to get AI response: " + e.getMessage()));
                    return 0;
                }
            })));
            
        // 新增reload命令
        dispatcher.register(literal("ai")
            .then(literal("reload")
            .requires(source -> source.hasPermissionLevel(4))
            .executes(context -> {
                try {
                    openAIService.reloadConfig();
                    AiForMinecraft.getInstance().reloadConfig();
                    context.getSource().sendFeedback(() -> Text.literal("AI配置已重新加载"), false);
                    return 1;
                } catch (Exception e) {
                    LOGGER.error("Failed to reload config", e);
                    context.getSource().sendError(Text.literal("配置重载失败"));
                    return 0;
                }
            })));
            
        // 新增config命令
        dispatcher.register(literal("ai")
            .then(literal("config")
            .requires(source -> source.hasPermissionLevel(4))
            .executes(context -> {
                ServerCommandSource source = context.getSource();
                OpenAIAPIConfig config = openAIService.getConfig();
                
                source.sendFeedback(() -> Text.literal("当前AI配置:"), false);
                source.sendFeedback(() -> Text.literal("API地址: " + config.getApiUrl()), false);
                source.sendFeedback(() -> Text.literal("模型: " + config.getModel()), false);
                source.sendFeedback(() -> Text.literal("最大token数: " + config.getMaxTokens()), false);
                source.sendFeedback(() -> Text.literal("温度参数: " + config.getTemperature()), false);
                source.sendFeedback(() -> Text.literal("系统提示: " + config.getSystemPrompt()), false);
                source.sendFeedback(() -> Text.literal("显示模型名称: " + config.shouldShowModelName()), false);
                source.sendFeedback(() -> Text.literal("处理思考标签: " + config.shouldRemoveThinkTags()), false);
                source.sendFeedback(() -> Text.literal("可配置选项: api_url, api_key, model, max_tokens, temperature, system_prompt, show_model_name, process_think_tags"), false);
                source.sendFeedback(() -> Text.literal("使用/ai config set <选项> <值> 修改配置"), false);
                return 1;
            })
            .then(literal("set")
                .then(argument("option", StringArgumentType.word())
                .then(argument("value", StringArgumentType.greedyString())
                .executes(context -> {
                    ServerCommandSource source = context.getSource();
                    String option = StringArgumentType.getString(context, "option");
                    String value = StringArgumentType.getString(context, "value");
                    
                    try {
                        AiForMinecraft.getInstance().updateConfig(option, value);
                        source.sendFeedback(() -> Text.literal("配置项 " + option + " 已更新为: " + value), false);
                        return 1;
                    } catch (Exception e) {
                        source.sendError(Text.literal("更新配置失败: " + e.getMessage()));
                        return 0;
                    }
                }))))));
    }
}