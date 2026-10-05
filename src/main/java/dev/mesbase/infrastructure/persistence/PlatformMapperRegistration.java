package dev.mesbase.infrastructure.persistence;
/** Explicit module contribution; registration does not choose any datasource or grant permission. */
public record PlatformMapperRegistration(Class<?> mapper) {
 public PlatformMapperRegistration{if(mapper==null||mapper.getAnnotation(PersistenceOwner.class)==null||mapper.getAnnotation(PersistenceOwner.class).value()!=PersistenceOwner.Kind.PLATFORM)throw new IllegalArgumentException("Explicit PLATFORM mapper required");}
}
