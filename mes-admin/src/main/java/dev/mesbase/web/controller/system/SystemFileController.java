package dev.mesbase.web.controller.system;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;


import dev.mesbase.common.core.controller.BaseController;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.core.page.TableDataInfo;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.service.impl.SysAttachmentServiceImpl;
import dev.mesbase.web.view.PageResultMapper;
import dev.mesbase.web.view.file.SysAttachmentAssembler;
import dev.mesbase.web.view.file.SysAttachmentResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/platform/files") @ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SystemFileController extends BaseController {
 private final SysAttachmentServiceImpl files;public SystemFileController(SysAttachmentServiceImpl files){this.files=files;}
 @GetMapping @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM) public TableDataInfo list(@RequestParam String resource){startPage();return PageResultMapper.map(files.list(resource,FactoryContext.current().orElse(null)), SysAttachmentAssembler::toResult);}
 @PostMapping @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM) public SysAttachmentResult upload(@RequestParam String resource,@RequestParam MultipartFile file)throws IOException{return SysAttachmentAssembler.toResult(files.upload(resource,file.getOriginalFilename(),file.getContentType(),file.getInputStream(),FactoryContext.current().orElse(null)));}
 @GetMapping("/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM) public SysAttachmentResult metadata(@PathVariable String id,@RequestParam String resource){return SysAttachmentAssembler.toResult(files.metadata(id,resource,FactoryContext.current().orElse(null)));}
 @DeleteMapping("/{id}") @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM) public AjaxResult delete(@PathVariable String id,@RequestParam String resource)throws IOException{return toAjax(files.delete(id,resource,FactoryContext.current().orElse(null)));}
 @GetMapping("/{id}/content") @EndpointAccess(value=EndpointAccess.Kind.AUTHENTICATED,permissionScope=EndpointAccess.PermissionScope.PLATFORM) public ResponseEntity<byte[]> download(@PathVariable String id,@RequestParam String resource)throws IOException{var row=files.metadata(id,resource,FactoryContext.current().orElse(null));String type=SetSafe.image(row.getFileType())?row.getFileType():"application/octet-stream";return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).header("X-Content-Type-Options","nosniff").header("Content-Disposition",ContentDisposition.attachment().filename(row.getOrignalName(),StandardCharsets.UTF_8).build().toString()).body(files.download(id,resource,FactoryContext.current().orElse(null)));}
 private static class SetSafe{static boolean image(String type){return Set.of("image/png","image/jpeg","image/gif","image/webp").contains(type);}}
}
