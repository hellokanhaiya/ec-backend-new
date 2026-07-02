package com.ecommerce.product;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Generates product copy and imagery from the little we know about a product
 * (title / category / summary). Uses keyless open-source Pollinations endpoints
 * — {@code text.pollinations.ai} for description text and {@code image.pollinations.ai}
 * for image URLs — via the native Java HTTP client (same approach as
 * {@code GeoLocationService}). Description generation always returns something: if
 * the model call fails or times out it falls back to a deterministic template so
 * the "Generate" button never dead-ends. Image URLs are returned as-is (the image
 * endpoint renders on GET), so no binaries are stored here.
 */
@Service
public class ProductAiService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${app.ai.text-base-url:https://text.pollinations.ai/}")
    private String textBaseUrl;

    @Value("${app.ai.image-base-url:https://image.pollinations.ai/prompt/}")
    private String imageBaseUrl;

    public GenerateDescriptionData generateDescription(GenerateDescriptionRequest request) {
        String title = safe(request == null ? null : request.title());
        String category = safe(request == null ? null : request.category());
        String summary = safe(request == null ? null : request.summary());
        String tone = request == null || request.tone() == null || request.tone().isBlank()
                ? "persuasive but honest"
                : request.tone().trim();

        String prompt = "Write a " + tone
                + " e-commerce product description as two short paragraphs followed by 3 to 5 bullet "
                + "points of key features. Product title: \"" + (title.isBlank() ? "this product" : title) + "\"."
                + (category.isBlank() ? "" : " Category: " + category + ".")
                + (summary.isBlank() ? "" : " Extra details: " + summary + ".")
                + " Use plain text. Start bullet lines with a dash. Do not repeat the title as a heading.";

        String generated = callTextModel(prompt);
        if (generated != null && !generated.isBlank()) {
            return new GenerateDescriptionData(toHtml(generated), "ai");
        }
        return new GenerateDescriptionData(fallbackHtml(title, category, summary), "fallback");
    }

    public GenerateImageData generateImage(GenerateImageRequest request) {
        String title = safe(request == null ? null : request.title());
        String category = safe(request == null ? null : request.category());
        String custom = safe(request == null ? null : request.prompt());

        String prompt = !custom.isBlank()
                ? custom
                : ("professional e-commerce product photo of " + (title.isBlank() ? "a product" : title)
                        + (category.isBlank() ? "" : ", " + category)
                        + ", studio lighting, white background, high detail, centered");

        String encoded = encodePath(prompt);
        String base = trimTrailingSlash(imageBaseUrl) + "/";
        // A couple of seeded variants so the merchant can pick.
        List<String> urls = new ArrayList<>();
        for (int seed : new int[] {1, 2}) {
            urls.add(base + encoded + "?width=1024&height=1024&nologo=true&seed=" + seed);
        }
        return new GenerateImageData(urls, prompt);
    }

    // --- text model call ---------------------------------------------------

    private String callTextModel(String prompt) {
        try {
            String encoded = encodePath(prompt);
            URI uri = URI.create(trimTrailingSlash(textBaseUrl) + "/" + encoded);
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "text/plain")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            String body = response.body();
            return body == null || body.isBlank() ? null : body.trim();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    // --- formatting helpers ------------------------------------------------

    /** Convert the model's plain text (paragraphs + dash/`*`/`•` bullets) into
     * simple, editor-friendly HTML. */
    static String toHtml(String text) {
        String[] lines = text.replace("\r\n", "\n").split("\n");
        StringBuilder html = new StringBuilder();
        List<String> bullets = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("•")) {
                bullets.add(escape(stripBullet(line)));
                continue;
            }
            if (!bullets.isEmpty()) {
                appendBullets(html, bullets);
                bullets.clear();
            }
            html.append("<p>").append(escape(line)).append("</p>");
        }
        if (!bullets.isEmpty()) {
            appendBullets(html, bullets);
        }
        return html.length() == 0 ? "<p>" + escape(text.trim()) + "</p>" : html.toString();
    }

    private static void appendBullets(StringBuilder html, List<String> bullets) {
        html.append("<ul>");
        for (String bullet : bullets) {
            html.append("<li>").append(bullet).append("</li>");
        }
        html.append("</ul>");
    }

    private static String stripBullet(String line) {
        String trimmed = line.startsWith("•") ? line.substring(1) : line.substring(2);
        return trimmed.trim();
    }

    private static String fallbackHtml(String title, String category, String summary) {
        String name = title.isBlank() ? "This product" : title;
        StringBuilder html = new StringBuilder();
        html.append("<p>")
                .append(escape(name))
                .append(category.isBlank() ? "" : " is a standout in " + escape(category))
                .append(". ")
                .append(summary.isBlank()
                        ? "Thoughtfully made to deliver everyday value and quality you can rely on."
                        : escape(summary))
                .append("</p>");
        html.append("<p>Designed with attention to detail, it balances quality, comfort and value — a dependable choice you'll reach for again and again.</p>");
        html.append("<ul>")
                .append("<li>Premium build and materials</li>")
                .append("<li>Everyday reliability and easy care</li>")
                .append(category.isBlank() ? "" : "<li>Ideal for " + escape(category) + "</li>")
                .append("<li>Backed by responsive customer support</li>")
                .append("</ul>");
        return html.toString();
    }

    // --- low-level helpers -------------------------------------------------

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String trimTrailingSlash(String value) {
        String v = value == null ? "" : value.trim();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
