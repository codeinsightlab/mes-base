package dev.mesbase.platform.codegen;
import java.nio.file.*;import java.io.IOException;import java.util.*;import java.security.*;
import static dev.mesbase.platform.codegen.CodegenException.Code.*;
import dev.mesbase.platform.codegen.CodegenRequest.OverwritePolicy;
/** Preflight both repos, stage, then publish; rollback writes on local failure, cleanup always. */
public final class ScaffoldWriter {
 public record Artifact(CodegenResult.Target target,String relativePath,String content) {}
 public List<CodegenResult.GeneratedFile> write(List<Artifact> artifacts,CodegenRequest request){
  Path backend=checkRoot(request.backendRoot()),frontend=checkRoot(request.frontendRoot());
  var paths=new LinkedHashMap<Path,Artifact>();var originals=new LinkedHashMap<Path,byte[]>();List<CodegenResult.GeneratedFile> manifest=new ArrayList<>();
  for(var artifact:artifacts){Path root=artifact.target()==CodegenResult.Target.FRONTEND?frontend:backend;Path p=root.resolve(artifact.relativePath()).normalize();if(!p.startsWith(root)||p.equals(root))throw new CodegenException(INVALID_REQUEST);noLinks(p);if(paths.put(p,artifact)!=null)throw new CodegenException(OUTPUT_CONFLICT);byte[] bytes=artifact.content().getBytes(java.nio.charset.StandardCharsets.UTF_8);manifest.add(new CodegenResult.GeneratedFile(artifact.target(),p.toString(),hash(bytes)));
   if(Files.exists(p)){try{byte[] original=Files.readAllBytes(p);if(Arrays.equals(original,bytes))continue;if(request.overwritePolicy()!=OverwritePolicy.REPLACE_GENERATED||!artifact.content().contains("MES-CODEGEN")||!new String(original,java.nio.charset.StandardCharsets.UTF_8).contains("MES-CODEGEN"))throw new CodegenException(OUTPUT_CONFLICT);originals.put(p,original);}catch(IOException e){throw new CodegenException(OUTPUT_FAILURE,e);}}
  }
  if(request.dryRun())return List.copyOf(manifest);
  Path staging=null;List<Path> written=new ArrayList<>();List<Path> createdDirs=new ArrayList<>();
  try{staging=Files.createTempDirectory("mes-codegen-");int i=0;for(var entry:paths.entrySet()){Files.writeString(staging.resolve(Integer.toString(i++)),entry.getValue().content());}
   i=0;for(var entry:paths.entrySet()){Path dest=entry.getKey();noLinks(dest);if(Files.exists(dest)&&!originals.containsKey(dest)){i++;continue;}if(originals.containsKey(dest)&&!Arrays.equals(originals.get(dest),Files.readAllBytes(dest)))throw new CodegenException(OUTPUT_CONFLICT);createDirectories(dest.getParent(),createdDirs);Path prepared=Files.createTempFile(dest.getParent(),".mes-codegen-",".tmp");try{Files.copy(staging.resolve(Integer.toString(i++)),prepared,StandardCopyOption.REPLACE_EXISTING);if(originals.containsKey(dest))Files.move(prepared,dest,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);else Files.move(prepared,dest);written.add(dest);}finally{Files.deleteIfExists(prepared);}}
   return List.copyOf(manifest);
  }catch(IOException|RuntimeException failure){Collections.reverse(written);for(Path p:written){try{if(originals.containsKey(p))Files.write(p,originals.get(p));else Files.deleteIfExists(p);}catch(IOException rollback){failure.addSuppressed(rollback);}}Collections.reverse(createdDirs);for(Path dir:createdDirs){try{Files.deleteIfExists(dir);}catch(IOException cleanup){failure.addSuppressed(cleanup);}}if(failure instanceof CodegenException e)throw e;throw new CodegenException(OUTPUT_FAILURE,failure);
  }finally{if(staging!=null){try(var files=Files.list(staging)){for(Path p:files.toList())Files.delete(p);Files.delete(staging);}catch(IOException e){throw new CodegenException(OUTPUT_FAILURE,e);}}}
 }
 static Path checkRoot(Path p){if(p==null)throw new CodegenException(INVALID_REQUEST);Path root=p.toAbsolutePath().normalize();if(root.getParent()==null)throw new CodegenException(INVALID_REQUEST);noLinks(root);return root;}
 static void noLinks(Path path){for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new CodegenException(INVALID_REQUEST);}
 static void createDirectories(Path path,List<Path> created)throws IOException{if(Files.exists(path))return;createDirectories(path.getParent(),created);Files.createDirectory(path);created.add(path);}
 static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
