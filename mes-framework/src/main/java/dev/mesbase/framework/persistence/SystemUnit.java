package dev.mesbase.framework.persistence;
import dev.mesbase.infrastructure.persistence.*;
import java.util.*;import java.util.function.Supplier;
/** Nested source services share one owned Platform session; external effects follow commit. */
public final class SystemUnit implements CommitActions {
 private record Work(OwnedSqlSession session,List<Runnable> committed,List<Runnable> rolledBack){}
 private final PlatformPersistence persistence;private final ThreadLocal<Work> current=new ThreadLocal<>();
 public SystemUnit(PlatformPersistence p){persistence=p;}
 public <T>T run(Supplier<T> action){
  if(current.get()!=null)return action.get();
  var committed=new ArrayList<Runnable>();var rolledBack=new ArrayList<Runnable>();T result;
  try{result=persistence.transaction(session->{current.set(new Work(session,committed,rolledBack));try{return action.get();}finally{current.remove();}});}
  catch(RuntimeException|Error failure){for(var cleanup:rolledBack)try{cleanup.run();}catch(RuntimeException e){failure.addSuppressed(e);}throw failure;}
  for(var effect:committed)effect.run();return result;
 }
 public void afterCommit(Runnable action){if(current.get()==null)throw new IllegalStateException("Owned transaction required");current.get().committed().add(action);}
 public void afterRollback(Runnable action){if(current.get()==null)throw new IllegalStateException("Owned transaction required");current.get().rolledBack().add(action);}
 public <T>T mapper(Class<T> type){return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
  if(method.getDeclaringClass()==Object.class)return method.invoke(this,args);
  if(current.get()==null)throw new IllegalStateException("Platform mapper requires owned service transaction");
  try{return method.invoke(current.get().session().mapper(type),args);}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
 }));}
}
