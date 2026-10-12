package dev.mesbase.framework.openapi;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * SOURCE_PRODUCT_PORT: Source OpenApiProperties; values remain YAML controlled.
 */
@Validated
@ConfigurationProperties(prefix = "open-api")
public class OpenApiProperties {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{1,64}")
    private String namespace = "base";
    @Min(1)
    @Max(86400)
    private long tokenTtlSeconds = 7200;
    @Min(1)
    private int rateWindowSeconds = 60;
    @Min(1)
    private int tokenRateLimit = 20;
    @Min(1)
    private int apiRateLimit = 120;
    @Valid
    private DataScope dataScope = new DataScope();

    public String keyPrefix() {
        return "open-api:" + namespace + ":v1:";
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String value) {
        namespace = value;
    }

    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(long value) {
        tokenTtlSeconds = value;
    }

    public int getRateWindowSeconds() {
        return rateWindowSeconds;
    }

    public void setRateWindowSeconds(int value) {
        rateWindowSeconds = value;
    }

    public int getTokenRateLimit() {
        return tokenRateLimit;
    }

    public void setTokenRateLimit(int value) {
        tokenRateLimit = value;
    }

    public int getApiRateLimit() {
        return apiRateLimit;
    }

    public void setApiRateLimit(int value) {
        apiRateLimit = value;
    }

    public DataScope getDataScope() {
        return dataScope;
    }

    public void setDataScope(DataScope value) {
        dataScope = value;
    }

    public static class DataScope {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }
    }
}
