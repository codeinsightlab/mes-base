package dev.mesbase.platform.codegen;
import java.util.*;import static dev.mesbase.platform.codegen.CodegenException.Code.*;
/** RuoYi metadata/model/Velocity/file classification flow adapted to Base. */
public final class DefaultMesCodegenService implements MesCodegenService {
 private final CodegenProfiles profiles;private final JdbcMetadataReader metadata;private final RuoyiTemplateRenderer renderer;private final ScaffoldWriter writer;
 public DefaultMesCodegenService(CodegenProfiles profiles){this.profiles=profiles;metadata=new JdbcMetadataReader();renderer=new RuoyiTemplateRenderer();writer=new ScaffoldWriter();}
 public CodegenResult generate(CodegenRequest request){
  if(request==null||request.scope()==null||request.overwritePolicy()==null)throw new CodegenException(INVALID_REQUEST);
  JdbcMetadataReader.identifier(request.tableName());JdbcMetadataReader.identifier(request.moduleName());if(!request.moduleName().matches("[a-z][a-z0-9_]{0,62}"))throw new CodegenException(INVALID_REQUEST);
  if(request.packageName()==null||!request.packageName().matches("dev\\.mesbase\\.(modules|platform)\\.[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*")||request.businessName()==null||request.businessName().isBlank()||request.businessName().length()>100||request.author()==null||request.author().length()>100)throw new CodegenException(INVALID_REQUEST);
  ScaffoldWriter.checkRoot(request.backendRoot());ScaffoldWriter.checkRoot(request.frontendRoot());String datasource=profiles.name(request.datasource());
  var table=metadata.read(profiles.datasource(datasource),request.schema(),request.tableName());var pk=table.columns().stream().filter(TableMetadata.Column::primaryKey).toList();
  if(pk.size()!=1||!Set.of("Long","Integer","String").contains(pk.getFirst().javaType())||!table.references().isEmpty())throw new CodegenException(UNSUPPORTED_SCHEMA);
  Set<String> fields=new HashSet<>();for(var c:table.columns()){if(!fields.add(c.javaField()))throw new CodegenException(UNSUPPORTED_SCHEMA);if(c.name().toLowerCase(Locale.ROOT).matches(".*(password|secret|token|credential|private_key).*"))throw new CodegenException(UNSUPPORTED_SCHEMA);if(c.name().toLowerCase(Locale.ROOT).endsWith("_id")&&!c.primaryKey()&&!c.name().equalsIgnoreCase("factory_id"))throw new CodegenException(UNSUPPORTED_SCHEMA);if(Set.of("user_id","user_name","file_id","external_id","owner_id","password","password_hash","secret","access_token","created_by","updated_by").contains(c.name().toLowerCase(Locale.ROOT)))throw new CodegenException(UNSUPPORTED_SCHEMA);}
  var factory=table.columns().stream().filter(c->c.name().equalsIgnoreCase("factory_id")).findFirst();
  if(request.scope()==CodegenRequest.ScopeKind.FACTORY_SCOPED){if(factory.isEmpty()||!factory.get().javaType().equals("String")||factory.get().nullable()||factory.get().size()>64||factory.get().primaryKey())throw new CodegenException(UNSUPPORTED_SCHEMA);for(var idx:table.indexes()){if(idx.unique()&&!idx.columns().equals(List.of(pk.getFirst().name()))&&idx.columns().stream().noneMatch(x->x.equalsIgnoreCase("factory_id")))throw new CodegenException(UNSUPPORTED_SCHEMA);}}
  else if(factory.isPresent())throw new CodegenException(UNSUPPORTED_SCHEMA);
  List<String> warnings=new ArrayList<>();warnings.add("Scaffold requires Factory/Permission/Resource/Domain/SQL/UI review; no automatic grants, runtime binding or production acceptance.");
  if(table.columns().stream().anyMatch(c->c.name().toLowerCase(Locale.ROOT).endsWith("status")))warnings.add("Status semantics are not inferred from numeric values; define a reviewed enum/dictionary before delivery.");
  warnings.add("Frontend permission state comes from server navigation; long IDs/decimals are transported as strings. No import/export/cache/aggregation generated.");
  var files=writer.write(renderer.render(request,table),request);return new CodegenResult(request.dryRun()?CodegenResult.Status.DRY_RUN:CodegenResult.Status.GENERATED,datasource,table.table(),table,files,List.copyOf(warnings));
 }
}
