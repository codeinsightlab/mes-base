package dev.mesbase.infrastructure.persistence;

import java.util.Objects;

public record DatasourceIdentity(String value) {
    public DatasourceIdentity {
        Objects.requireNonNull(value);
        if (!value.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid datasource identity");
    }
}
