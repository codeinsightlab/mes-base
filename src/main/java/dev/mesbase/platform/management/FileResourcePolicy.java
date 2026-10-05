package dev.mesbase.platform.management;
import dev.mesbase.core.factory.FactoryScope;
/** Future business modules must prove resource ownership through this port before attachment. */
public interface FileResourcePolicy { void verify(String ownerId,FactoryScope scope,String resource); }
