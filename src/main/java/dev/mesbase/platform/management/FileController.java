package dev.mesbase.platform.management;
import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;import org.springframework.http.*;
import dev.mesbase.platform.web.*;import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.management.PlatformContracts.FileView;import java.io.IOException;
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.platform.enabled",havingValue="true")
@RestController @RequestMapping("/api/platform/files")
public final class FileController {
 private final FileService files;
 public FileController(FileService files){this.files=files;}
 @PostMapping @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM) public FileView upload(@RequestParam String resource,@RequestParam MultipartFile file,Authentication actor)throws IOException{try(var input=file.getInputStream()){return files.uploadForActor(actor.getName(),FactoryContext.current().orElse(null),resource,file.getOriginalFilename(),input);}}
 @GetMapping("/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM) public FileView metadata(@PathVariable String id,@RequestParam String resource,Authentication actor){return files.metadataForActor(id,actor.getName(),FactoryContext.current().orElse(null),resource);}
 @GetMapping("/{id}/content") @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM) public ResponseEntity<byte[]> download(@PathVariable String id,@RequestParam String resource,Authentication actor)throws IOException{return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header("Content-Disposition","attachment").header("X-Content-Type-Options","nosniff").body(files.downloadForActor(id,actor.getName(),FactoryContext.current().orElse(null),resource));}
}
