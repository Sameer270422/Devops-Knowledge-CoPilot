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

/** Alternate chat provider — set app.llm.provider=openai to use this instead of Claude.
 *  Not a @Service; see the note in AnthropicChatClient for why LlmConfig instantiates
 *  this directly instead of using @ConditionalOnProperty. */
public class OpenAiChatClient implements ChatLlmClient {

    private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiChatClient(String apiKey) {
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
            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", userMessage);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmException("Chat request failed (" + response.statusCode() + "): " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            return root.get("choices").get(0).get("message").get("content").asText();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Failed to reach LLM provider", e);
        }
    }
}
