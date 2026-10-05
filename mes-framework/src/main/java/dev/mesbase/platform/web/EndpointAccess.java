package dev.mesbase.platform.web;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface EndpointAccess { Kind value(); String permission() default ""; PermissionScope permissionScope() default PermissionScope.FACTORY; enum PermissionScope { PLATFORM, FACTORY } enum Kind { PUBLIC, AUTHENTICATED, AUTHORIZED } }
