package com.copilot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls OpenAI's embeddings endpoint directly over java.net.http — no SDK dependency for
 * what is one JSON request/response. Anthropic doesn't offer an embeddings API, so this
 * is used regardless of which provider is configured for chat generation.
 */
@Service
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final String ENDPOINT = "https://api.openai.com/v1/embeddings";
    private static final String MODEL = "text-embedding-3-small";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiEmbeddingClient(@Value("${app.embedding.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (apiKey.isBlank()) {
            throw new LlmException("app.embedding.api-key is not configured");
        }
        try {
            String body = mapper.writeValueAsString(new EmbedRequest(MODEL, texts));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmException("Embedding request failed (" + response.statusCode() + "): " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            List<float[]> results = new ArrayList<>();
            for (JsonNode item : root.get("data")) {
                JsonNode vectorNode = item.get("embedding");
                float[] vector = new float[vectorNode.size()];
                for (int i = 0; i < vectorNode.size(); i++) {
                    vector[i] = (float) vectorNode.get(i).asDouble();
                }
                results.add(vector);
            }
            return results;
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Failed to reach embedding provider", e);
        }
    }

    private record EmbedRequest(String model, List<String> input) {}
}
