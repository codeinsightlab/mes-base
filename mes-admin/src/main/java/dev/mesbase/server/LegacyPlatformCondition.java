package dev.mesbase.server;
public final class LegacyPlatformCondition implements org.springframework.context.annotation.Condition {
 public boolean matches(org.springframework.context.annotation.ConditionContext c,org.springframework.core.type.AnnotatedTypeMetadata m){return c.getEnvironment().getProperty("mes.legacy-test-only",Boolean.class,false)&&!c.getEnvironment().getProperty("mes.system.enabled",Boolean.class,false);}
}