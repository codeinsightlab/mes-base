package dev.mesbase.platform.codegen;

import java.util.Properties;
import java.nio.file.Path;
import org.apache.velocity.app.VelocityEngine;

/**
 * Adapted from upstream RuoYi VelocityInitializer, MIT, pinned 13db1fcef36bee9ce45d2d636a1d4e8f5ed5bbc3.
 * Constants.UTF8 replaced by UTF-8; engine is instance-owned for concurrent developer profiles.
 * Velocity2.4 classpath-loader keys replace legacy global/static initialization.
 */
final class VelocityTemplateInitializer {
    private VelocityTemplateInitializer() {
    }

    static VelocityEngine initialize() {
        return initialize(null);
    }

    static VelocityEngine initialize(Path templateRoot) {
        Properties p = new Properties();
        p.setProperty("resource.loaders", "class");
        p.setProperty("resource.loader.class.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        if (templateRoot != null) {
            p.setProperty("resource.loaders", "file");
            p.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
            p.setProperty("resource.loader.file.path", templateRoot.toAbsolutePath().toString());
            p.setProperty("resource.loader.file.cache", "true");
            p.setProperty("resource.loader.file.modification_check_interval", "-1");
        }
        p.setProperty("resource.default_encoding", "UTF-8");
        p.setProperty("runtime.strict_mode.enable", "true");
        try {
            VelocityEngine engine = new VelocityEngine(p);
            engine.init();
            return engine;
        } catch (RuntimeException failure) {
            throw new CodegenException(CodegenException.Code.TEMPLATE_FAILURE, failure);
        }
    }
}
