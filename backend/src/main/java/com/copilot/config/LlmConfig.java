package com.copilot.config;

import com.copilot.llm.AnthropicChatClient;
import com.copilot.llm.ChatLlmClient;
import com.copilot.llm.OpenAiChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single place that decides which ChatLlmClient implementation the app runs with,
 * based on app.llm.provider ("anthropic" or "openai", defaulting to anthropic for
 * anything else). The value is trimmed and lower-cased before comparison — deliberately
 * more forgiving than an exact match, since this value comes from an env var that's
 * passed through docker-compose.yml and (for local dev) a hand-edited .env file, both of
 * which can introduce stray whitespace a strict comparison would silently choke on.
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public ChatLlmClient chatLlmClient(
            @Value("${app.llm.provider:anthropic}") String provider,
            @Value("${app.llm.api-key:}") String apiKey
    ) {
        String normalized = provider == null ? "anthropic" : provider.trim().toLowerCase();
        log.info("Using LLM provider: {}", normalized);

        if ("openai".equals(normalized)) {
            return new OpenAiChatClient(apiKey);
        }
        // Anthropic is the default for "anthropic" and for anything unrecognized, rather
        // than failing to start — a typo'd provider value shouldn't take the whole app down.
        return new AnthropicChatClient(apiKey);
    }
}
