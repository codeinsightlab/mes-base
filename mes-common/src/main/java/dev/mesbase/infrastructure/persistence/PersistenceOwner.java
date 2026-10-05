package dev.mesbase.infrastructure.persistence;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface PersistenceOwner { Kind value(); enum Kind { PLATFORM, BUSINESS } }
