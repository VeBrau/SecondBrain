package me.sailex.secondbrain.llm.openrouter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.sailex.secondbrain.exception.LLMServiceException;
import me.sailex.secondbrain.history.Message;
import me.sailex.secondbrain.llm.LLMClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class OpenRouterClient implements LLMClient {

    private static final String BASE_URL = "https://openrouter.ai/api/v1";
    private static final String HTTP_REFERER = "https://github.com/sailex428/SecondBrain";
    private static final String APP_TITLE = "SecondBrain";

    private final String model;
    private final String apiKey;
    private final int timeout;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public OpenRouterClient(String model, String apiKey, int timeout) {
        this.model = model;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public Message chat(List<Message> messages) {
        try {
            HttpRequest request = requestBuilder("/chat/completions")
                    .POST(HttpRequest.BodyPublishers.ofString(createChatRequest(messages), StandardCharsets.UTF_8))
                    .build();
            JsonNode responseBody = sendRequest(request);
            JsonNode assistantMessage = responseBody.path("choices").path(0).path("message");
            String content = assistantMessage.path("content").asText("");
            if (content.isBlank()) {
                throw new IOException("OpenRouter returned an empty response body");
            }
            String role = assistantMessage.path("role").asText("assistant");
            return new Message(content, role);
        } catch (Exception e) {
            throw new LLMServiceException("Could not generate Response for prompt: " + messages.get(messages.size() - 1).getMessage(), e);
        }
    }

    @Override
    public void checkServiceIsReachable() {
        try {
            HttpRequest request = requestBuilder("/models")
                    .GET()
                    .build();
            sendRequest(request);
        } catch (Exception e) {
            throw new LLMServiceException("OpenRouter is not reachable", e);
        }
    }

    @Override
    public void stopService() {
        // nothing to stop
    }

    private String createChatRequest(List<Message> messages) throws IOException {
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", model);
        ArrayNode requestMessages = requestBody.putArray("messages");

        for (Message message : messages) {
            ObjectNode messageNode = requestMessages.addObject();
            messageNode.put("role", message.getRole());
            messageNode.put("content", message.getMessage());
        }

        return mapper.writeValueAsString(requestBody);
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(timeout))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("HTTP-Referer", HTTP_REFERER)
                .header("X-Title", APP_TITLE);
    }

    private JsonNode sendRequest(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException(response.statusCode() + " - " + response.uri() + " responseBody: " + response.body());
        }
        return mapper.readTree(response.body());
    }
}
