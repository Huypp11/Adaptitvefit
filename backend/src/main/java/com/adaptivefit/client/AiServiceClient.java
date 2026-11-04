package com.adaptivefit.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.adaptivefit.observability.PerformanceTiming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Goi dich vu FastAPI.  */
@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiFactor(
            @JsonProperty("feature") String feature, @JsonProperty("label") String label,
            @JsonProperty("value") double value, @JsonProperty("reference") double reference,
            @JsonProperty("direction") String direction, @JsonProperty("impact_pp") double impactPp,
            @JsonProperty("text") String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiPrediction(
            @JsonProperty("risk_score") double riskScore, @JsonProperty("risk_level") String riskLevel,
            @JsonProperty("base_risk") double baseRisk, @JsonProperty("model_version") String modelVersion,
            @JsonProperty("algorithm") String algorithm, @JsonProperty("is_demo_model") boolean demoModel,
            @JsonProperty("shap_factors") List<AiFactor> factors,
            @JsonProperty("other_impact_pp") Double otherImpactPp, @JsonProperty("summary") String summary,
            @JsonProperty("notes") List<String> notes) {}

    private final RestClient client;
    private final boolean enabled;

    public AiServiceClient(@Value("${app.ai.base-url}") String baseUrl,
                           @Value("${app.ai.internal-key:}") String internalKey,
                           @Value("${app.ai.timeout-ms:3000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.enabled = internalKey != null && !internalKey.isBlank();
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Internal-Key", internalKey == null ? "" : internalKey)
                .build();
        if (!enabled) {
            log.info("AI_INTERNAL_KEY chua dat: bo qua dich vu AI, dung uoc tinh nguy co theo lich su");
        }
    }

    public Optional<AiPrediction> predict(Map<String, Double> features) {
        if (!enabled) {
            PerformanceTiming.event("ai.http_predict", "result=disabled_fallback");
            return Optional.empty();
        }
        return PerformanceTiming.measure("ai.http_predict", () -> {
            try {
                String requestId = PerformanceTiming.currentRequestId();
                AiPrediction prediction = client.post().uri("/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(headers -> {
                            if (requestId != null) headers.set("X-Request-ID", requestId);
                        })
                        .body(features)
                        .retrieve()
                        .body(AiPrediction.class);
                PerformanceTiming.event("ai.http_predict.result",
                        "result=" + (prediction == null ? "empty_response" : "success"));
                return Optional.ofNullable(prediction);
            } catch (RestClientException e) {
                PerformanceTiming.event("ai.http_predict.result",
                        "result=fallback exception=" + e.getClass().getSimpleName());
                log.warn("Khong goi duoc dich vu AI: {}", e.getMessage());
                return Optional.empty();
            }
        });
    }
}
