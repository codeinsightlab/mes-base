package dev.mesbase.infrastructure.persistence;
import java.util.function.Function;
import dev.mesbase.core.factory.FactoryScope;
/** Services choose a scoped unit of work; they do not choose deployment topology. */
public interface BusinessPersistence { <T> T transaction(FactoryScope scope, Function<OwnedSqlSession,T> work); }
