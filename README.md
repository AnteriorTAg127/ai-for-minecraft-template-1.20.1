# AI For Minecraft 模组

一个为Minecraft 1.20.1设计的Fabric模组，通过OpenAI API为游戏内添加AI助手功能。

## 功能特性

- 游戏内AI聊天命令 `/ai <消息>`
- 管理员配置命令 `/ai config` 查看当前配置
- 管理员配置更新命令 `/ai config set <选项> <值>`
- 配置重载命令 `/ai reload`
- 支持自定义OpenAI API端点
- 可配置模型参数（温度、最大token数等）
- 自动分割长消息避免全服广播

## 安装要求

1. Minecraft 1.20.1
2. Fabric Loader
3. Fabric API
4. 有效的OpenAI API密钥

## 配置说明

首次运行会在`config/ai-for-minecraft.json`生成默认配置文件，包含以下可配置项：

- `api_url`: OpenAI API端点（默认: https://api.openai.com/v1/chat/completions）
- `api_key`: 你的OpenAI API密钥
- `model`: 使用的模型（默认: gpt-3.5-turbo）
- `max_tokens`: 最大token数（默认: 1000）
- `temperature`: 温度参数（默认: 0.7）
- `system_prompt`: 系统提示词（默认: "You are a helpful AI assistant in Minecraft."）
- `show_model_name`: 是否在响应中显示模型名称（默认: true）
- `process_think_tags`: 是否处理思考标签（默认: true）

## 使用示例

1. 普通玩家使用AI聊天：
```
/ai 如何建造一个自动农场？
```

2. 管理员查看配置：
```
/ai config
```

3. 管理员更新配置：
```
/ai config set temperature 0.5
```

4. 重载配置：
```
/ai reload
```

## 许可证

本项目采用MIT许可证，详见[LICENSE](LICENSE)文件。