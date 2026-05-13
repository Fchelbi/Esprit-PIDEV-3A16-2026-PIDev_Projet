package services;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

public class GeminiService {

    private final Client client;

    public GeminiService() {
        String apiKey = System.getenv("GEMINI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not set.");
        }

        this.client = Client.builder()
                .apiKey(apiKey)
                .build();
    }

    private String askGemini(String prompt) {
        GenerateContentResponse response = client.models.generateContent(
                "gemini-2.5-flash",
                prompt,
                null
        );

        return response.text().trim();
    }

    public String generateForumPost(String topic) {
        String prompt = """
                You are helping write a supportive community forum post for EchoCare.

                Topic: %s

                Return exactly this format:
                Title: ...
                Content: ...

                Rules:
                - simple supportive language
                - no diagnosis
                - no medication advice
                - no dangerous medical advice
                - max 120 words
                """.formatted(topic);

        return askGemini(prompt);
    }

    public String generateRandomAdvice() {
        String prompt = """
                Generate one short supportive wellbeing advice for a community feed.

                Rules:
                - 1 or 2 sentences only
                - practical and kind
                - no diagnosis
                - no medication advice
                - no emergency claims
                """;

        return askGemini(prompt);
    }
}