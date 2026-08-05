package com.copilot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Not a @Service — instantiated directly by LlmConfig based on app.llm.provider. An
 * earlier version used @ConditionalOnProperty on this class and OpenAiChatClient to pick
 * between them, which turned out to be fragile: it depends on an exact string match
 * against an env-var-sourced property, and something as small as a stray character (e.g.
 * a Windows CRLF line ending leaking into a .env value) makes BOTH conditions fail
 * silently, with no bean registered and a confusing "no qualifying bean" error at
 * startup. LlmConfig trims the value explicitly instead, which is both more robust and a
 * single obvious place to see how provider selection works.
 */
public class AnthropicChatClient implements ChatLlmClient {

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-5";
    private static final String API_VERSION = "2023-06-01";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public AnthropicChatClient(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String generate(String systemPrompt, String userMessage) {
        if (apiKey.isBlank()) {
            throw new LlmException("app.llm.api-key is not configured");
        }
        try {
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", MODEL);
            requestBody.put("max_tokens", 1024);
            requestBody.put("system", systemPrompt);
            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmException("Chat request failed (" + response.statusCode() + "): " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            return root.get("content").get(0).get("text").asText();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Failed to reach LLM provider", e);
        }
    }
}
