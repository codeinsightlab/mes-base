package dev.mesbase.platform.codegen;

import dev.mesbase.platform.codegen.ScaffoldWriter.Artifact;
import dev.mesbase.platform.codegen.TableMetadata.Column;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.web.util.HtmlUtils;
import static dev.mesbase.platform.codegen.CodegenResult.Target.BACKEND;
import static dev.mesbase.platform.codegen.CodegenResult.Target.FRONTEND;
import static dev.mesbase.platform.codegen.CodegenResult.Target.PERMISSION;

/** RuoYi's Velocity metadata -> context -> named templates -> classified file pipeline.
 * MIT upstream attribution: docs/legal/ruoyi-MIT-LICENSE.txt. Base-specific contracts replace legacy Entity APIs.
 */
public final class RuoyiTemplateRenderer {
 private final VelocityEngine engine;
 public RuoyiTemplateRenderer(){engine=RuoyiVelocityInitializer.initialize();}
 private String merge(String template,Map<String,Object> model){try{var out=new StringWriter();engine.getTemplate("codegen/base/"+template,"UTF-8").merge(new VelocityContext(model),out);return out.toString();}catch(RuntimeException e){throw new CodegenException(CodegenException.Code.TEMPLATE_FAILURE,e);}}

 private static String string(String s){return JdbcMetadataReader.safe(s).replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");}
 private static String entityType(Column c){return c.javaType();}
 private static String viewType(Column c){return Set.of("Long","java.math.BigDecimal").contains(c.javaType())?"String":c.javaType();}
 private static String cap(String s){return Character.toUpperCase(s.charAt(0))+s.substring(1);}
 private static String validation(Column c,boolean required){String a=required?(c.javaType().equals("String")?"@jakarta.validation.constraints.NotBlank ":"@jakarta.validation.constraints.NotNull "):"";if(c.javaType().equals("String"))a+="@jakarta.validation.constraints.Size(max="+Math.min(c.size(),10000)+") ";if(c.javaType().equals("java.math.BigDecimal"))a+="@jakarta.validation.constraints.Digits(integer="+Math.max(1,c.size()-c.scale())+",fraction="+c.scale()+") ";return a;}
 public List<Artifact> render(CodegenRequest r,TableMetadata t){
  UnaryOperator<String> quote=identifier->{JdbcMetadataReader.identifier(identifier);return t.identifierQuote()+identifier+t.identifierQuote();};
  boolean scoped=r.scope()==CodegenRequest.ScopeKind.FACTORY_SCOPED;Column pk=t.columns().stream().filter(Column::primaryKey).findFirst().orElseThrow();String n=cap(JdbcMetadataReader.camel(r.tableName())),p=r.packageName(),name=JdbcMetadataReader.camel(r.tableName()),route="/api/"+r.moduleName()+"/"+r.tableName().replace('_','-').toLowerCase(Locale.ROOT),perm=r.moduleName()+":"+r.tableName().toLowerCase(Locale.ROOT);
  List<Column> writable=t.columns().stream().filter(c->!c.primaryKey()&&!c.name().equalsIgnoreCase("factory_id")).toList();List<Column> create=new ArrayList<>();if(!pk.autoIncrement())create.add(pk);create.addAll(writable);
  Map<String,Object> ctx=new HashMap<>();ctx.put("packageName",p);ctx.put("ClassName",n);ctx.put("className",name);ctx.put("functionName",string(r.businessName()));ctx.put("functionNameHtml",HtmlUtils.htmlEscape(r.businessName()));ctx.put("author",string(r.author()));ctx.put("moduleName",r.moduleName());ctx.put("route",route);ctx.put("permission",perm);ctx.put("scoped",scoped);ctx.put("permissionScope",scoped?"factory":"platform");ctx.put("idType",pk.javaType());ctx.put("pkField",pk.javaField());
  List<Artifact> out=new ArrayList<>();String dir=p.replace('.','/')+"/";
  BiConsumer<String,String> emitJava=(file,body)->{ctx.put("body",JavaSourceImports.explicit(body));out.add(new Artifact(BACKEND,"src/main/java/"+dir+file+".java",merge("java.vm",ctx)));};
  StringBuilder entity=new StringBuilder("public final class "+n+"Entity"+(scoped?" implements dev.mesbase.core.factory.FactoryOwned":"")+" {\n");
  for(Column c:t.columns()){entity.append(" /** ").append(c.comment()==null?c.name():c.comment()).append(" */\n private ").append(entityType(c)).append(" ").append(c.javaField()).append(";\n public ").append(entityType(c)).append(" get").append(cap(c.javaField())).append("(){return ").append(c.javaField()).append(";}\n public void set").append(cap(c.javaField())).append("(").append(entityType(c)).append(" value){this.").append(c.javaField()).append("=value;}\n");}
  if(scoped)entity.append(" @Override public dev.mesbase.core.factory.FactoryId factoryId(){return new dev.mesbase.core.factory.FactoryId(factoryId);}\n");emitJava.accept(n+"Entity",entity+"}\n");
  for(var entry:Map.of("CreateCommand",create,"UpdateCommand",writable).entrySet())emitJava.accept(n+entry.getKey(),"public record "+n+entry.getKey()+"("+entry.getValue().stream().map(c->validation(c,!c.nullable())+entityType(c)+" "+c.javaField()).collect(Collectors.joining(", "))+") {}\n");
  emitJava.accept(n+"Vo","public record "+n+"Vo("+t.columns().stream().map(c->viewType(c)+" "+c.javaField()).collect(Collectors.joining(", "))+") {}\n");
  emitJava.accept(n+"PageVo","public record "+n+"PageVo(java.util.List<"+n+"Vo> items,long total,int offset,int limit) {}\n");
  String queryFields=writable.stream().map(c->(c.javaType().equals("String")?"@jakarta.validation.constraints.Size(max=100) ":c.javaType().equals("java.time.LocalDateTime")?"@org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) ":c.javaType().equals("java.time.LocalDate")?"@org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) ":"")+entityType(c)+" "+c.javaField()).collect(Collectors.joining(", "));
  emitJava.accept(n+"QueryDto","public record "+n+"QueryDto(@jakarta.validation.constraints.Min(0) Integer offset,@jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) Integer limit"+(queryFields.isEmpty()?"":", "+queryFields)+") {}\n");
  emitJava.accept(n+"Permissions","public final class "+n+"Permissions { private "+n+"Permissions(){} public static final String READ=\""+perm+":read\", CREATE=\""+perm+":create\", UPDATE=\""+perm+":update\", DELETE=\""+perm+":delete\"; }\n");
  String ownership=scoped?"BUSINESS":"PLATFORM";

  emitJava.accept(n+"Mapper","public interface "+n+"Mapper {\n java.util.List<"+n+"Entity> list(@org.apache.ibatis.annotations.Param(\"factory\")String factory,@org.apache.ibatis.annotations.Param(\"q\")"+n+"QueryDto q,@org.apache.ibatis.annotations.Param(\"offset\")int offset,@org.apache.ibatis.annotations.Param(\"limit\")int limit);\n long count(@org.apache.ibatis.annotations.Param(\"factory\")String factory,@org.apache.ibatis.annotations.Param(\"q\")"+n+"QueryDto q);\n "+n+"Entity detail(@org.apache.ibatis.annotations.Param(\"factory\")String factory,@org.apache.ibatis.annotations.Param(\"id\")"+pk.javaType()+" id);\n int insert("+n+"Entity row); int update(@org.apache.ibatis.annotations.Param(\"factory\")String factory,@org.apache.ibatis.annotations.Param(\"row\")"+n+"Entity row);\n int delete(@org.apache.ibatis.annotations.Param(\"factory\")String factory,@org.apache.ibatis.annotations.Param(\"id\")"+pk.javaType()+" id);\n}\n");
  // Adapted RuoYi mapper result-map / SQL fragments / selective insert; scopes are explicit parameters, no auth decision in XML.
  String table=(t.schema()==null?"":quote.apply(t.schema())+".")+quote.apply(t.table());
  ctx.put("sqlTable",table);ctx.put("sqlPk",quote.apply(pk.name()));ctx.put("pkAuto",pk.autoIncrement());ctx.put("pkColumnName",pk.name());
  ctx.put("sqlFactory",scoped?quote.apply(t.columns().stream().filter(c->c.name().equalsIgnoreCase("factory_id")).findFirst().orElseThrow().name()):"");
  ctx.put("columns",t.columns().stream().map(c->Map.of("javaField",c.javaField(),"columnName",c.name(),"sqlName",quote.apply(c.name()),"primary",c.primaryKey(),"string",c.javaType().equals("String"))).toList());
  ctx.put("editColumns",writable.stream().map(c->Map.of("javaField",c.javaField(),"sqlName",quote.apply(c.name()),"string",c.javaType().equals("String"))).toList());
  List<Column> inserted=new ArrayList<>(create);if(scoped)inserted.add(t.columns().stream().filter(c->c.name().equalsIgnoreCase("factory_id")).findFirst().orElseThrow());
  ctx.put("insertColumns",inserted.stream().map(c->Map.of("javaField",c.javaField(),"sqlName",quote.apply(c.name()))).toList());
  out.add(new Artifact(BACKEND,"src/main/resources/"+dir+n+"Mapper.xml",merge("mapper.xml.vm",ctx)));
  ctx.put("scopeParam",scoped?"dev.mesbase.core.factory.FactoryScope scope,":"");out.add(new Artifact(BACKEND,"src/main/java/"+dir+"I"+n+"Service.java",merge("service.java.vm",ctx)));
  String scopeParam=scoped?"dev.mesbase.core.factory.FactoryScope scope,":"",run=scoped?"persistence.transaction(scope,s->":"persistence.transaction(s->",factory=scoped?"scope.factoryId().value()":"null";
  String permissionCheck="";String persistenceType=scoped?"dev.mesbase.infrastructure.persistence.BusinessPersistence":"dev.mesbase.infrastructure.persistence.PlatformPersistence";
  StringBuilder service=new StringBuilder((scoped?"":"@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name=\"mes.platform.enabled\",havingValue=\"true\")\n")+"@org.springframework.stereotype.Service\npublic final class "+n+"ServiceImpl implements I"+n+"Service {\n private final "+persistenceType+" persistence;public "+n+"ServiceImpl("+persistenceType+" persistence){this.persistence=persistence;}\n");
  service.append(" private static "+n+"Vo view("+n+"Entity row){if(row==null)throw new dev.mesbase.core.error.FoundationException(dev.mesbase.core.error.ErrorCode.NOT_FOUND);return new "+n+"Vo("+t.columns().stream().map(c->viewType(c).equals(entityType(c))?"row.get"+cap(c.javaField())+"()":"row.get"+cap(c.javaField())+"()==null?null:row.get"+cap(c.javaField())+"().toString()").collect(Collectors.joining(","))+");}\n");
  service.append("public "+n+"PageVo list("+scopeParam+n+"QueryDto q){int offset=q.offset()==null?0:q.offset(),limit=q.limit()==null?20:q.limit();if(offset<0||limit<1||limit>100)throw new dev.mesbase.core.error.FoundationException(dev.mesbase.core.error.ErrorCode.BAD_REQUEST);return "+run+"{var m=s.mapper("+n+"Mapper.class);return new "+n+"PageVo(m.list("+factory+",q,offset,limit).stream().map("+n+"ServiceImpl::view).toList(),m.count("+factory+",q),offset,limit);});}\n");
  service.append("public "+n+"Vo detail("+scopeParam+pk.javaType()+" id){return "+run+"view(s.mapper("+n+"Mapper.class).detail("+factory+",id)));}\n");
  for(String action:List.of("create","update")){
   boolean creating=action.equals("create");service.append("public "+n+"Vo "+action+"("+scopeParam+(creating?"":pk.javaType()+" id,")+n+(creating?"CreateCommand":"UpdateCommand")+" in){return "+run+"{var m=s.mapper("+n+"Mapper.class);"+(creating?"":"view(m.detail("+factory+",id));")+"var row=new "+n+"Entity();");
   if(scoped)service.append("row.setFactoryId(scope.factoryId().value());");if(!creating)service.append("row.set"+cap(pk.javaField())+"(id);");
   for(Column c:creating?create:writable)service.append("row.set"+cap(c.javaField())+"(in."+c.javaField()+"());");
   service.append("if(m."+(creating?"insert(row)":"update("+factory+",row)")+"!=1)throw new dev.mesbase.core.error.FoundationException(dev.mesbase.core.error.ErrorCode.RESOURCE_CONFLICT);return view(m.detail("+factory+",row.get"+cap(pk.javaField())+"()));});}\n");
  }
  service.append("public void delete("+scopeParam+pk.javaType()+" id){"+run+"{var m=s.mapper("+n+"Mapper.class);view(m.detail("+factory+",id));if(m.delete("+factory+",id)!=1)throw new dev.mesbase.core.error.FoundationException(dev.mesbase.core.error.ErrorCode.RESOURCE_CONFLICT);return null;});}\n}\n");emitJava.accept(n+"ServiceImpl",service.toString());
  String scopeArg=scoped?"dev.mesbase.platform.execution.FactoryContext.required(),":"",permissionScope=scoped?"":",permissionScope=dev.mesbase.platform.web.EndpointAccess.PermissionScope.PLATFORM";
  StringBuilder controller=new StringBuilder((scoped?"":"@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name=\"mes.platform.enabled\",havingValue=\"true\")\n")+"@org.springframework.web.bind.annotation.RestController\n@org.springframework.web.bind.annotation.RequestMapping(\""+route+"\")\npublic final class "+n+"Controller { private final I"+n+"Service service;public "+n+"Controller(I"+n+"Service service){this.service=service;}\n");
  String auth="@dev.mesbase.platform.web.EndpointAccess(value=dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,permission="+n+"Permissions.";
  controller.append("@org.springframework.web.bind.annotation.GetMapping "+auth+"READ"+permissionScope+") public "+n+"PageVo list(@jakarta.validation.Valid @org.springframework.web.bind.annotation.ModelAttribute "+n+"QueryDto q){return service.list("+scopeArg+"q);}\n");
  controller.append("@org.springframework.web.bind.annotation.GetMapping(\"/{id}\") "+auth+"READ"+permissionScope+") public "+n+"Vo detail(@org.springframework.web.bind.annotation.PathVariable "+pk.javaType()+" id){return service.detail("+scopeArg+"id);}\n");
  controller.append("@org.springframework.web.bind.annotation.PostMapping "+auth+"CREATE"+permissionScope+") public "+n+"Vo create(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody "+n+"CreateCommand in){return service.create("+scopeArg+"in);}\n");
  controller.append("@org.springframework.web.bind.annotation.PutMapping(\"/{id}\") "+auth+"UPDATE"+permissionScope+") public "+n+"Vo update(@org.springframework.web.bind.annotation.PathVariable "+pk.javaType()+" id,@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody "+n+"UpdateCommand in){return service.update("+scopeArg+"id,in);}\n");
  controller.append("@org.springframework.web.bind.annotation.DeleteMapping(\"/{id}\") @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) "+auth+"DELETE"+permissionScope+") public void delete(@org.springframework.web.bind.annotation.PathVariable "+pk.javaType()+" id){service.delete("+scopeArg+"id);}\n}\n");emitJava.accept(n+"Controller",controller.toString());
  ctx.put("pkField",pk.javaField());ctx.put("fields",t.columns().stream().map(c->Map.of("name",c.javaField(),"type",Set.of("String","Long","java.math.BigDecimal").contains(c.javaType())?"string":c.javaType().startsWith("java.time")?"string":c.javaType().equals("Boolean")?"boolean":"number","nullable",c.nullable(),"label",string(c.comment()==null||c.comment().isBlank()?(c.primaryKey()?"编号":c.javaField()):c.comment()).replace("'","\\'"),"kind",c.javaType().equals("java.math.BigDecimal")?"decimal":c.javaType().equals("java.time.LocalDate")?"date":c.javaType().startsWith("java.time")?"datetime":c.javaType().equals("Integer")?"number":c.javaType().equals("Boolean")?"boolean":"text","readonly",(c.primaryKey()&&c.autoIncrement())||c.name().equalsIgnoreCase("factory_id"),"size",Math.min(c.size(),10000),"required",!c.nullable())).toList());
  ctx.put("writableFields",create.stream().map(Column::javaField).toList());ctx.put("updateFields",writable.stream().map(Column::javaField).toList());
  out.add(new Artifact(FRONTEND,"src/api/"+r.moduleName()+"/"+name+".ts",merge("api.ts.vm",ctx)));
  out.add(new Artifact(FRONTEND,"src/views/"+r.moduleName()+"/"+name+"/index.vue",merge("index.vue.vm",ctx)));
  out.add(new Artifact(PERMISSION,"docs/generated/"+r.moduleName()+"/"+name+"-permissions.md","<!-- MES-CODEGEN -->\n# "+r.businessName()+"\n\nScope: "+r.scope()+"; Mapper owner: "+ownership+"\n\nPath: `"+route+"`\n\n"+List.of("read","create","update","delete").stream().map(x->"- `"+perm+":"+x+"`\n").collect(Collectors.joining())+"\nPermission declarations only; no automatic Role/Menu grants or invented menu SQL. Mapper ownership follows its fixed package; no Mapper annotation or central registration is required.\n"));
  ctx.put("body","public class "+n+"ScaffoldTest {\n @org.junit.jupiter.api.Test void contractsStayScoped(){org.junit.jupiter.api.Assertions.assertEquals(dev.mesbase.infrastructure.persistence.PersistenceOwner.Kind."+ownership+",dev.mesbase.infrastructure.persistence.MapperPackages.owner("+n+"Mapper.class));for(var method:"+n+"Controller.class.getDeclaredMethods()){var access=method.getAnnotation(dev.mesbase.platform.web.EndpointAccess.class);org.junit.jupiter.api.Assertions.assertNotNull(access);org.junit.jupiter.api.Assertions.assertEquals(dev.mesbase.platform.web.EndpointAccess.Kind.AUTHORIZED,access.value());}for(var c:"+n+"CreateCommand.class.getRecordComponents())org.junit.jupiter.api.Assertions.assertNotEquals(\"factoryId\",c.getName());}\n}\n");ctx.put("body",JavaSourceImports.explicit((String)ctx.get("body")));out.add(new Artifact(BACKEND,"src/test/java/"+dir+n+"ScaffoldTest.java",merge("java.vm",ctx)));
  return List.copyOf(out);
 }
}
