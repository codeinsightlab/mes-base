package dev.mesbase.core.security;
import java.util.Objects;
public record Permission(String code) { public Permission { Objects.requireNonNull(code); if(!code.matches("[a-z][A-Za-z0-9.:_-]{0,99}")) throw new IllegalArgumentException("Invalid permission"); } }
