package dev.mesbase.platform.management;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.infrastructure.platform.PlatformMapper;
import dev.mesbase.core.error.*;
import java.util.function.Function;
/** One PLATFORM unit of work per use case; never falls back to Business. */
public final class PlatformStore {
 private final PlatformPersistence persistence;
 public PlatformStore(PlatformPersistence persistence){this.persistence=persistence;}
 public <T> T run(Function<PlatformMapper,T> work){
  try{return persistence.transaction(s->work.apply(s.mapper(PlatformMapper.class)));}
  catch(org.apache.ibatis.exceptions.PersistenceException e){
   Throwable root=e; while(root.getCause()!=null)root=root.getCause();
   if(root instanceof java.sql.SQLException sql && sql.getSQLState()!=null && sql.getSQLState().startsWith("23")) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT,e);
   throw e;
  }
 }
}
