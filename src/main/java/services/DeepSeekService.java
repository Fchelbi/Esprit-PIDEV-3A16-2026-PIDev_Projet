package services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DeepSeekService {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ModerationResult moderatePost(String title, String content) {

        // Local safety filter first
        String fullText = ((title == null ? "" : title) + " " + (content == null ? "" : content)).toLowerCase();

        String[] blockedWords = {
                "bad word", "kys", "gtfo"
        };

        for (String word : blockedWords) {
            if (fullText.contains(word)) {
                return new ModerationResult(true, "Contains inappropriate language: " + word);
            }
        }

        try {
            String apiKey = System.getenv("DEEPSEEK_API_KEY");

            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("DEEPSEEK_API_KEY is not set.");
            }

            String prompt = """
                    You are moderating a community forum post.

                    Classify the post as APPROVED or FLAGGED.

                    Flag if it contains:
                    - profanity or vulgar language, even a single swear word
                    - insults, harassment, hate, threats
                    - sexual content
                    - self-harm instructions
                    - dangerous medical advice
                    - spam or scam content

                    Important:
                    If the title or content contains words like "fuck", "shit", "bitch", or other vulgar language, classify it as FLAGGED.

                    Return exactly:
                    STATUS: APPROVED or FLAGGED
                    REASON: short reason

                    Title: %s
                    Content: %s
                    """.formatted(title, content);

            String body = """
                    {
                      "model": "deepseek-chat",
                      "messages": [
                        {
                          "role": "user",
                          "content": "%s"
                        }
                      ],
                      "temperature": 0.1
                    }
                    """.formatted(escapeJson(prompt));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.deepseek.com/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            String responseBody = response.body();
            String aiText = extractContent(responseBody);

            boolean flagged = aiText.toUpperCase().contains("STATUS: FLAGGED");

            String reason = "AI moderation completed.";
            int idx = aiText.toUpperCase().indexOf("REASON:");
            if (idx >= 0) {
                reason = aiText.substring(idx + "REASON:".length()).trim();
            }

            return new ModerationResult(flagged, reason);

        } catch (Exception e) {
            return new ModerationResult(true, "AI moderation failed: " + e.getMessage());
        }
    }

    private String extractContent(String json) {
        String marker = "\"content\":\"";
        int start = json.indexOf(marker);

        if (start < 0) {
            return json;
        }

        start += marker.length();
        int end = json.indexOf("\"", start);

        if (end < 0) {
            return json.substring(start);
        }

        return json.substring(start, end)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    public record ModerationResult(boolean flagged, String reason) {}
}