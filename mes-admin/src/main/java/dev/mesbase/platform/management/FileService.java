package dev.mesbase.platform.management;
import java.nio.file.*;import java.io.*;import java.time.*;import java.util.*;
import dev.mesbase.core.factory.FactoryScope;import dev.mesbase.core.error.*;
import dev.mesbase.infrastructure.platform.PlatformRows.File;
import dev.mesbase.platform.management.PlatformContracts.FileView;
/** Private storage: UUID path, exact owner + exact optional factory + business-resource binding. */
public final class FileService {
 private final PlatformStore store;private final FileStorage storage;private final FileResourcePolicy policy;private final long maxBytes;
 public FileService(PlatformStore store,Path root,long maxBytes){this(store,new dev.mesbase.infrastructure.platform.LocalPrivateFileStorage(root),maxBytes);}
 public FileService(PlatformStore store,FileStorage storage,long maxBytes){this(store,storage,maxBytes,(owner,scope,resource)->{if(!"personal".equals(resource))throw new FoundationException(ErrorCode.UNPROCESSABLE);});}
 public FileService(PlatformStore store,FileStorage storage,long maxBytes,FileResourcePolicy policy){this.store=store;this.storage=storage;this.policy=policy;this.maxBytes=maxBytes;if(maxBytes<1||maxBytes>100_000_000)throw new IllegalArgumentException("Invalid upload limit");}
 public FileView uploadForActor(String username,FactoryScope scope,String resource,String name,InputStream input)throws IOException{return upload(actorId(username),scope,resource,name,input);}
 public FileView metadataForActor(String id,String username,FactoryScope scope,String resource){return metadata(id,actorId(username),scope,resource);}
 public byte[] downloadForActor(String id,String username,FactoryScope scope,String resource)throws IOException{return download(id,actorId(username),scope,resource);}
 private String actorId(String username){return store.run(m->PlatformService.required(m.userByUsername(username)).id());}
 public FileView upload(String owner,FactoryScope scope,String resource,String name,InputStream input)throws IOException{
  if(resource==null||!resource.matches("[A-Za-z0-9:_-]{1,128}"))throw new FoundationException(ErrorCode.BAD_REQUEST);
  policy.verify(owner,scope,resource);
  String clean=name==null?"attachment":name.replace('\\','/');clean=clean.substring(clean.lastIndexOf('/')+1).replaceAll("[\\r\\n\\p{Cntrl}]","");if(clean.isBlank()||clean.length()>160)throw new FoundationException(ErrorCode.BAD_REQUEST);
  String id=UUID.randomUUID().toString();long size=storage.write(id,input,maxBytes);
  var row=new File(id,owner,scope==null?null:scope.factoryId().value(),resource,clean,size,LocalDateTime.now(Clock.systemUTC()));
  try{store.run(m->{m.insertFile(row);return null;});}catch(RuntimeException e){try{storage.delete(id);}catch(IOException cleanup){e.addSuppressed(cleanup);}throw e;}
  return view(row);
 }
 private static FileView view(File f){return new FileView(f.id(),f.ownerId(),f.factoryId(),f.resource(),f.originalName(),f.size(),f.createdAt());}
 public FileView metadata(String id,String owner,FactoryScope scope,String resource){var result=store.run(m->{var f=PlatformService.required(m.findFile(id));if(!f.ownerId().equals(owner)||!Objects.equals(f.factoryId(),scope==null?null:scope.factoryId().value())||!f.resource().equals(resource))throw new FoundationException(ErrorCode.NOT_FOUND);return view(f);});policy.verify(owner,scope,resource);return result;}
 public byte[] download(String id,String owner,FactoryScope scope,String resource)throws IOException{var f=metadata(id,owner,scope,resource);return storage.read(id,f.size());}
}
