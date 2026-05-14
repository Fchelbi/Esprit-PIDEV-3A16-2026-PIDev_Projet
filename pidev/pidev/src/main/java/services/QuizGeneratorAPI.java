package services;

import utils.VideoPlayerUtil;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class QuizGeneratorAPI {

    // ── mistral-7b-instruct is much smarter than gemma-3-1b ──────────────
    private static final String OPENROUTER_URL   = "https://openrouter.ai/api/v1/chat/completions";
    private static final String OPENROUTER_KEY   = "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";
    private static final String OPENROUTER_MODEL = "mistralai/mistral-7b-instruct:free";

    // ════════════════════════════════════════════════════════════════════
    //  PUBLIC — Generate from title + description
    // ════════════════════════════════════════════════════════════════════

    public List<GeneratedQuestion> generateQuestions(String title, String description, int count) throws Exception {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Titre requis.");
        count = Math.max(1, Math.min(20, count));
        String context = (description != null && description.trim().length() > 10)
                ? description.trim() : title;

        System.out.println("[QuizGen] Calling Mistral-7B for: " + title);
        try {
            List<GeneratedQuestion> result = callOpenRouter(title, context, count);
            System.out.println("[QuizGen] AI returned: " + result.size() + " questions");
            if (!result.isEmpty()) return result;
        } catch (Exception e) {
            System.err.println("[QuizGen] OpenRouter failed: " + e.getMessage());
        }

        System.out.println("[QuizGen] Using fallback for: " + title);
        return generateFallback(title, count);
    }

    // ════════════════════════════════════════════════════════════════════
    //  PUBLIC — Generate from YouTube transcript
    // ════════════════════════════════════════════════════════════════════

    public List<GeneratedQuestion> generateFromYouTube(String youtubeUrl, int count) throws Exception {
        count = Math.max(1, Math.min(20, count));
        String videoId = VideoPlayerUtil.extractYouTubeId(youtubeUrl);
        if (videoId == null || videoId.isBlank())
            throw new IllegalArgumentException("URL YouTube invalide : " + youtubeUrl);

        String videoTitle = fetchVideoTitle(videoId);
        System.out.println("[QuizGen] YouTube title: " + videoTitle);

        String transcript = fetchTranscript(videoId);
        String context;
        if (transcript != null && transcript.length() > 150) {
            String t = transcript.length() > 3000 ? transcript.substring(0, 3000) : transcript;
            context = "Transcription de la vidéo :\n" + t;
            System.out.println("[QuizGen] Transcript: " + transcript.length() + " chars");
        } else {
            context = "Vidéo sur le sujet : " + videoTitle;
            System.out.println("[QuizGen] No transcript — using title");
        }
        return generateQuestions(videoTitle, context, count);
    }

    // ════════════════════════════════════════════════════════════════════
    //  OPENROUTER — Mistral 7B
    // ════════════════════════════════════════════════════════════════════

    private List<GeneratedQuestion> callOpenRouter(String title, String context, int count) throws Exception {
        long seed = System.nanoTime() % 99999;
        String[] angles = {
                "Focus on key definitions and concepts.",
                "Focus on practical applications and real examples.",
                "Focus on common mistakes and how to avoid them.",
                "Focus on steps, processes and methods.",
                "Focus on benefits, advantages and outcomes."
        };
        String angle = angles[(int)(seed % angles.length)];

        // Prompt in English works much better with Mistral
        String prompt = "You are an expert quiz creator. Generate exactly " + count
                + " multiple choice questions about: \"" + title + "\". "
                + angle + " "
                + "Reference content: " + context.substring(0, Math.min(context.length(), 1500)) + ". "
                + "RULES: "
                + "1) Each question must be UNIQUE and different from the others. "
                + "2) Questions must be in FRENCH. "
                + "3) Respond ONLY with a valid JSON array, no text before or after, no markdown. "
                + "4) Exact format: [{\"question\":\"Question text?\","
                + "\"options\":[\"Option A\",\"Option B\",\"Option C\",\"Option D\"],"
                + "\"correct\":0,\"points\":10}] "
                + "correct = index (0-3) of the correct answer. points = 5 or 10. "
                + "Generate " + count + " questions. JSON ONLY.";

        String body = "{\"model\":\"" + OPENROUTER_MODEL + "\","
                + "\"messages\":[{\"role\":\"user\",\"content\":"
                + jsonStr(prompt) + "}],"
                + "\"max_tokens\":2500,"
                + "\"temperature\":0.85}";

        String raw = httpPost(OPENROUTER_URL, body,
                "Authorization", "Bearer " + OPENROUTER_KEY,
                "HTTP-Referer", "https://echocare.app",
                "X-Title", "EchoCare");

        System.out.println("[QuizGen] Response (300 chars): "
                + raw.substring(0, Math.min(300, raw.length())));

        // Extract content from OpenRouter response
        String content = extractStringField(raw, "content");
        if (content == null || content.isBlank()) {
            System.err.println("[QuizGen] No content field found");
            return List.of();
        }

        System.out.println("[QuizGen] Content (200 chars): "
                + content.substring(0, Math.min(200, content.length())));

        // Strip markdown code blocks if present
        content = content.replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "").trim();

        List<GeneratedQuestion> result = parseJsonQuestions(content);
        // Remove duplicates
        List<GeneratedQuestion> deduped = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (GeneratedQuestion gq : result) {
            if (seen.add(gq.questionText.toLowerCase().trim()))
                deduped.add(gq);
        }
        return deduped;
    }

    // ════════════════════════════════════════════════════════════════════
    //  FALLBACK — always returns questions, never empty
    // ════════════════════════════════════════════════════════════════════

    public List<GeneratedQuestion> generateFallback(String topic, int count) {
        List<GeneratedQuestion> bank = new ArrayList<>();
        bank.add(mkQ("Quelle est la définition principale de \"" + topic + "\" ?",
                new String[]{"Une compétence comportementale essentielle","Un logiciel de gestion","Un diplôme académique","Un processus administratif"}, 0, 10));
        bank.add(mkQ("Quel est le premier obstacle pour développer \"" + topic + "\" ?",
                new String[]{"La résistance au changement","Le manque d'équipement","L'absence de formation","Le coût financier"}, 0, 5));
        bank.add(mkQ("Comment mesure-t-on le succès dans \"" + topic + "\" ?",
                new String[]{"Par des résultats concrets et observables","Par le nombre d'heures","Par les diplômes","Par l'ancienneté"}, 0, 10));
        bank.add(mkQ("Quelle méthode est la plus efficace pour apprendre \"" + topic + "\" ?",
                new String[]{"La pratique active avec retour d'expérience","La lecture seule","Les cours magistraux","La mémorisation"}, 0, 10));
        bank.add(mkQ("Dans quel contexte \"" + topic + "\" est-il le plus utile ?",
                new String[]{"En situation de travail collaboratif","Uniquement en formation","Seulement pour les managers","Dans les grandes entreprises"}, 0, 5));
        bank.add(mkQ("Quelle erreur courante doit-on éviter avec \"" + topic + "\" ?",
                new String[]{"Appliquer une approche unique sans s'adapter","Trop pratiquer","Demander trop de feedback","Travailler en équipe"}, 0, 10));
        bank.add(mkQ("Comment \"" + topic + "\" améliore-t-il les relations professionnelles ?",
                new String[]{"En favorisant la compréhension mutuelle","En imposant des règles strictes","En évitant les conflits","En formalisant tout par écrit"}, 0, 5));
        bank.add(mkQ("Quel indicateur montre qu'on progresse dans \"" + topic + "\" ?",
                new String[]{"Adapter son comportement selon le contexte","Connaître la théorie","Avoir un certificat","Lire des livres"}, 0, 5));
        bank.add(mkQ("Pourquoi \"" + topic + "\" est-il important dans le monde professionnel ?",
                new String[]{"Il complète les compétences techniques","Il remplace les compétences techniques","Il est réservé aux cadres","Il est optionnel"}, 0, 10));
        bank.add(mkQ("Quelle attitude favorise l'apprentissage de \"" + topic + "\" ?",
                new String[]{"L'ouverture d'esprit et la curiosité","La certitude de tout savoir","La prudence excessive","L'évitement des situations nouvelles"}, 0, 5));
        bank.add(mkQ("Comment intégrer \"" + topic + "\" dans sa routine quotidienne ?",
                new String[]{"Par de petites actions répétées chaque jour","Par une seule formation intensive","Par des lectures hebdomadaires","Par des réunions mensuelles"}, 0, 5));
        bank.add(mkQ("Quelle compétence complémentaire renforce le mieux \"" + topic + "\" ?",
                new String[]{"L'écoute active et l'empathie","La maîtrise des outils informatiques","La gestion de projet","La comptabilité"}, 0, 10));
        bank.add(mkQ("Quel rôle joue \"" + topic + "\" dans la résolution de conflits ?",
                new String[]{"Il facilite le dialogue et les compromis","Il évite d'aborder les sujets difficiles","Il impose une solution unique","Il n'a aucun rôle"}, 0, 5));
        bank.add(mkQ("Comment un manager peut-il développer \"" + topic + "\" dans son équipe ?",
                new String[]{"En créant des opportunités de pratique","En imposant des règles strictes","En organisant des formations annuelles","En sanctionnant les erreurs"}, 0, 10));
        bank.add(mkQ("Quel impact \"" + topic + "\" a-t-il sur la satisfaction au travail ?",
                new String[]{"Il améliore le bien-être et l'engagement","Il n'a aucun impact","Il augmente le stress","Il complique les relations"}, 0, 10));
        bank.add(mkQ("Quelle est la meilleure façon d'évaluer ses progrès en \"" + topic + "\" ?",
                new String[]{"Solliciter des retours réguliers de ses pairs","S'auto-évaluer uniquement","Passer un examen annuel","Lire ses anciens cours"}, 0, 5));
        bank.add(mkQ("En quoi \"" + topic + "\" diffère-t-il d'une simple connaissance théorique ?",
                new String[]{"Il s'exprime par des comportements observables","C'est la même chose","Il est plus facile à acquérir","Il ne nécessite pas de pratique"}, 0, 10));
        bank.add(mkQ("Quel signe montre une bonne maîtrise de \"" + topic + "\" ?",
                new String[]{"S'adapter efficacement à des situations variées","Connaître toutes les théories","Avoir suivi de nombreuses formations","Ne jamais faire d'erreurs"}, 0, 10));
        bank.add(mkQ("Quelle est la relation entre \"" + topic + "\" et la confiance en soi ?",
                new String[]{"Maîtriser ce sujet renforce la confiance professionnelle","Il n'y a aucune relation","La confiance nuit à l'apprentissage","La confiance en soi est innée"}, 0, 5));
        bank.add(mkQ("Comment savoir si on a besoin de progresser en \"" + topic + "\" ?",
                new String[]{"Par des difficultés répétées dans des situations similaires","Si on n'a pas de diplôme","Si les collègues le disent","Si on n'aime pas le sujet"}, 0, 5));

        int offset = Math.abs(topic.hashCode()) % bank.size();
        List<GeneratedQuestion> result = new ArrayList<>();
        for (int i = 0; i < Math.min(count, bank.size()); i++) {
            result.add(bank.get((i + offset) % bank.size()));
        }
        return result;
    }

    private GeneratedQuestion mkQ(String text, String[] opts, int correct, int points) {
        GeneratedQuestion gq = new GeneratedQuestion();
        gq.questionText = text;
        gq.options = List.of(opts);
        gq.correctIndex = correct;
        gq.points = points;
        return gq;
    }

    // ════════════════════════════════════════════════════════════════════
    //  YOUTUBE HELPERS
    // ════════════════════════════════════════════════════════════════════

    private String fetchVideoTitle(String videoId) {
        try {
            String response = httpGet("https://www.youtube.com/oembed?url="
                    + "https://www.youtube.com/watch?v=" + videoId + "&format=json");
            String title = extractStringField(response, "title");
            if (title != null && !title.isBlank()) return title;
        } catch (Exception e) { System.err.println("[QuizGen] oEmbed: " + e.getMessage()); }
        return videoId;
    }

    private String fetchTranscript(String videoId) {
        try {
            String html = httpGetWithAgent("https://www.youtube.com/watch?v=" + videoId,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120");
            String subtitleUrl = extractSubtitleUrl(html);
            if (subtitleUrl == null) return null;
            return parseSubtitleXml(httpGet(subtitleUrl));
        } catch (Exception e) { System.err.println("[QuizGen] Transcript: " + e.getMessage()); return null; }
    }

    private String extractSubtitleUrl(String html) {
        try {
            int idx = html.indexOf("\"captionTracks\":");
            if (idx == -1) return null;
            int urlIdx = html.indexOf("\"baseUrl\":\"", idx);
            if (urlIdx == -1) return null;
            urlIdx += 11;
            StringBuilder sb = new StringBuilder();
            while (urlIdx < html.length()) {
                char c = html.charAt(urlIdx);
                if (c == '"') break;
                if (c == '\\' && urlIdx + 5 < html.length() && html.charAt(urlIdx+1) == 'u') {
                    try { sb.append((char) Integer.parseInt(html.substring(urlIdx+2, urlIdx+6), 16)); urlIdx += 6; continue; }
                    catch (NumberFormatException ignored) {}
                }
                sb.append(c); urlIdx++;
            }
            String url = sb.toString();
            return url.startsWith("http") ? url : null;
        } catch (Exception e) { return null; }
    }

    private String parseSubtitleXml(String xml) {
        if (xml == null || xml.isBlank()) return null;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < xml.length()) {
            int ts = xml.indexOf("<text ", i); if (ts == -1) break;
            int cs = xml.indexOf('>', ts); if (cs == -1) break;
            int ce = xml.indexOf("</text>", ++cs); if (ce == -1) break;
            String word = xml.substring(cs, ce)
                    .replace("&amp;","&").replace("&lt;","<").replace("&gt;",">")
                    .replace("&quot;","\"").replace("&#39;","'").replace("\n"," ")
                    .replaceAll("<[^>]+>","").trim();
            if (!word.isEmpty()) sb.append(word).append(" ");
            i = ce + 7;
        }
        return sb.toString().trim();
    }

    // ════════════════════════════════════════════════════════════════════
    //  HTTP
    // ════════════════════════════════════════════════════════════════════

    private String httpPost(String urlStr, String body, String... headers) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(20_000);
        conn.setReadTimeout(60_000);
        for (int i = 0; i + 1 < headers.length; i += 2)
            conn.setRequestProperty(headers[i], headers[i+1]);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) throw new Exception("HTTP " + code + " no body");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        if (code < 200 || code >= 300)
            throw new Exception("HTTP " + code + ": " + sb.toString().substring(0, Math.min(400, sb.length())));
        return sb.toString();
    }

    private String httpGet(String urlStr) throws Exception { return httpGetWithAgent(urlStr, "EchoCare/1.0"); }

    private String httpGetWithAgent(String urlStr, String userAgent) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setConnectTimeout(10_000); conn.setReadTimeout(20_000);
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) throw new Exception("HTTP " + code);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════════
    //  JSON PARSING
    // ════════════════════════════════════════════════════════════════════

    private List<GeneratedQuestion> parseJsonQuestions(String raw) {
        List<GeneratedQuestion> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;
        String arr = extractJsonArray(raw);
        if (arr == null) { System.err.println("[QuizGen] No JSON array in: " + raw.substring(0, Math.min(200, raw.length()))); return result; }
        int depth = 0, start = -1;
        for (int i = 0; i < arr.length(); i++) {
            char c = arr.charAt(i);
            if (c == '"') { i++; while (i < arr.length()) { char s = arr.charAt(i); if (s == '\\') i++; else if (s == '"') break; i++; } }
            else if (c == '{') { if (depth == 0) start = i; depth++; }
            else if (c == '}') { depth--; if (depth == 0 && start != -1) { GeneratedQuestion gq = parseOneQuestion(arr.substring(start, i+1)); if (gq != null) result.add(gq); start = -1; } }
        }
        System.out.println("[QuizGen] Parsed " + result.size() + " questions");
        return result;
    }

    private GeneratedQuestion parseOneQuestion(String block) {
        try {
            GeneratedQuestion gq = new GeneratedQuestion();
            gq.questionText = extractStringField(block, "question");
            if (gq.questionText == null || gq.questionText.isBlank()) return null;
            gq.options = new ArrayList<>();
            int optStart = block.indexOf("\"options\"");
            if (optStart != -1) {
                int arrS = block.indexOf('[', optStart), arrE = findMatchingBracket(block, arrS);
                if (arrS != -1 && arrE != -1) gq.options = parseStringArray(block.substring(arrS+1, arrE));
            }
            if (gq.options.size() < 2) return null;
            String cs = extractNumberField(block, "correct");
            gq.correctIndex = 0;
            if (cs != null) try { gq.correctIndex = Integer.parseInt(cs.trim()); } catch (NumberFormatException ignored) {}
            if (gq.correctIndex < 0 || gq.correctIndex >= gq.options.size()) gq.correctIndex = 0;
            String ps = extractNumberField(block, "points");
            gq.points = 5;
            if (ps != null) try { gq.points = Integer.parseInt(ps.trim()); } catch (NumberFormatException ignored) {}
            return gq;
        } catch (Exception e) { return null; }
    }

    private String extractJsonArray(String text) {
        if (text == null) return null;
        int depth = 0, start = -1; boolean inStr = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inStr) { if (c == '\\') i++; else if (c == '"') inStr = false; }
            else { if (c == '"') inStr = true; else if (c == '[') { if (depth == 0) start = i; depth++; } else if (c == ']') { depth--; if (depth == 0 && start != -1) return text.substring(start, i+1); } }
        }
        return null;
    }

    private String extractStringField(String json, String key) {
        if (json == null) return null;
        int idx = json.indexOf("\"" + key + "\""); if (idx == -1) return null;
        idx += key.length() + 2;
        while (idx < json.length() && (json.charAt(idx) == ' ' || json.charAt(idx) == ':')) idx++;
        if (idx >= json.length() || json.charAt(idx) != '"') return null;
        idx++;
        StringBuilder sb = new StringBuilder();
        while (idx < json.length()) {
            char c = json.charAt(idx);
            if (c == '\\') { idx++; if (idx >= json.length()) break; char e = json.charAt(idx); switch(e){case '"':sb.append('"');break;case '\\':sb.append('\\');break;case 'n':sb.append('\n');break;default:sb.append(e);} }
            else if (c == '"') break;
            else sb.append(c);
            idx++;
        }
        return sb.toString();
    }

    private String extractNumberField(String json, String key) {
        if (json == null) return null;
        int idx = json.indexOf("\"" + key + "\""); if (idx == -1) return null;
        idx += key.length() + 2;
        while (idx < json.length() && (json.charAt(idx) == ' ' || json.charAt(idx) == ':')) idx++;
        if (idx >= json.length()) return null;
        int end = idx;
        while (end < json.length() && ",}\n\r ".indexOf(json.charAt(end)) == -1) end++;
        return json.substring(idx, end).trim();
    }

    private List<String> parseStringArray(String s) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            while (i < s.length() && s.charAt(i) != '"') i++;
            if (i >= s.length()) break;
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '\\') { i++; if (i < s.length()) { char e = s.charAt(i); switch(e){case '"':sb.append('"');break;case '\\':sb.append('\\');break;case 'n':sb.append('\n');break;default:sb.append(e);} } }
                else if (c == '"') break;
                else sb.append(c);
                i++;
            }
            String val = sb.toString().trim();
            if (!val.isEmpty()) result.add(val);
            i++;
        }
        return result;
    }

    private int findMatchingBracket(String text, int start) {
        if (start < 0 || start >= text.length()) return -1;
        int depth = 0; boolean inStr = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inStr) { if (c == '\\') i++; else if (c == '"') inStr = false; }
            else { if (c == '"') inStr = true; else if (c == '[') depth++; else if (c == ']') { depth--; if (depth == 0) return i; } }
        }
        return -1;
    }

    private String jsonStr(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"")
                .replace("\n","\\n").replace("\r","").replace("\t"," ") + "\"";
    }

    public static class GeneratedQuestion {
        public String       questionText;
        public List<String> options;
        public int          correctIndex;
        public int          points;
    }
}