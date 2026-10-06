package dev.mesbase.server.managed;

import dev.mesbase.core.error.*;
import dev.mesbase.core.factory.*;
import dev.mesbase.framework.persistence.SystemUnit;
import dev.mesbase.infrastructure.persistence.*;
import dev.mesbase.server.DruidPools;
import dev.mesbase.system.factory.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.sql.DataSource;
import java.util.*;

/** SOURCE_PRODUCT_PORT: ManagedDatasourceService. Platform records own configuration; pools are node-local. */
public final class ManagedDatasourceService {
    public record DatasourceForm(Long datasourceId, Long revision,
            @NotBlank @Size(max=64) String datasourceName,
            @NotBlank @Size(max=255) @Pattern(regexp="[a-zA-Z0-9.-]+") String dbHost,
            @Min(1) @Max(65535) int dbPort,
            @NotBlank @Size(max=64) @Pattern(regexp="[a-zA-Z0-9_]+") String dbName,
            @NotBlank @Size(max=128) String dbUsername,
            @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @Size(max=512) String password) {
        @Override public String toString(){return "DatasourceForm[REDACTED]";}
    }
    public record FactoryForm(@NotBlank @Size(max=64) @Pattern(regexp="[a-zA-Z0-9_-]+") String factoryId,
            @NotBlank @Size(max=64) String enterpriseId,@NotBlank @Size(max=32) String factoryCode,
            @NotBlank @Size(max=64) String factoryName,Long datasourceId,
            @NotNull @Pattern(regexp="ENABLED|DISABLED") String status,Long revision) {}
    public record DatasourceView(Long datasourceId,String datasourceName,String dbHost,int dbPort,String dbName,
            String dbUsername,String status,long revision,String runtimeStatus,
        String factoryId,String factoryName,String statusName,String runtimeStatusName,
        boolean initialized,String runtimeError,String lastErrorTime,String ddlGeneratedAt,String nodeId) {}
    public record FactoryView(String factoryId,String enterpriseId,String factoryCode,String factoryName,
            Long datasourceId,String status,long revision,String datasourceName) {}
    private final PlatformPersistence persistence;
    private final SystemUnit unit;
    private final ManagedDatasourceMapper sources;
    private final ManagedFactoryMapper factories;
    private final DatasourcePasswordCipher cipher;
    private final RuntimeRegistry registry;
    private final DataSource platform;
    private final DruidPools.Settings settings;
    private final Set<Class<?>> mappers;
    private final java.nio.file.Path ddlRoot;
    private final String nodeId="node-"+UUID.randomUUID();
    private final Map<DatasourceIdentity,String> physical=new HashMap<>();
    private record DdlCache(long revision,java.time.Instant at,DdlView view) {}
    private final java.util.concurrent.ConcurrentHashMap<Long,DdlCache> ddlCache=new java.util.concurrent.ConcurrentHashMap<>();

    public ManagedDatasourceService(PlatformPersistence persistence,SystemUnit unit,DatasourcePasswordCipher cipher,
            RuntimeRegistry registry,DataSource platform,DruidPools.Settings settings,Set<Class<?>> mappers,java.nio.file.Path ddlRoot) {
        this.persistence=persistence;this.unit=unit;this.sources=unit.mapper(ManagedDatasourceMapper.class);
        this.factories=unit.mapper(ManagedFactoryMapper.class);this.cipher=cipher;this.registry=registry;
        this.platform=platform;this.settings=settings;this.mappers=Set.copyOf(mappers);this.ddlRoot=ddlRoot.toAbsolutePath().normalize();
    }
    private static void changed(int count){if(count!=1)throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);}
    private static <T>T required(T row){if(row==null)throw new FoundationException(ErrorCode.NOT_FOUND);return row;}
    private static DatasourceIdentity identity(long id){return new DatasourceIdentity("managed-"+id);}
    private ManagedDatasource locked(long id){return required(sources.lock(id));}
    private void editable(ManagedDatasource row){if("ENABLED".equals(row.status())||factories.bound(row.datasourceId())!=0)throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);}
    private DatasourceView view(ManagedDatasource s,ManagedFactory f){
        var loaded=f!=null&&registry.contains(new BusinessRuntimeBinding(new FactoryId(f.factoryId()),identity(s.datasourceId()),s.revision()));
        return new DatasourceView(s.datasourceId(),s.datasourceName(),s.dbHost(),s.dbPort(),s.dbName(),s.dbUsername(),s.status(),s.revision(),loaded?"MANAGED":"UNMANAGED",f==null?null:f.factoryId(),f==null?null:f.factoryName(),
            "ENABLED".equals(s.status())?"已加入管理":"未加入管理",loaded?"已加载":"未加载",loaded,null,null,generatedAt(s),nodeId);
    }
    private String generatedAt(ManagedDatasource row) {
        var cached=ddlCache.get(row.datasourceId());if(cached!=null&&cached.revision()==row.revision())return cached.at().toString();
        var file=ddlRoot.resolve("datasource-"+row.datasourceId()+"-r"+row.revision()+".sql");
        try {return java.nio.file.Files.isRegularFile(file)?java.nio.file.Files.getLastModifiedTime(file).toInstant().toString():null;}
        catch(java.io.IOException ex){throw new IllegalStateException("DDL snapshot metadata read failed",ex);}
    }
    private static FactoryView view(ManagedFactory f,String datasourceName){return new FactoryView(f.factoryId(),f.enterpriseId(),f.factoryCode(),f.factoryName(),f.datasourceId(),f.status(),f.revision(),datasourceName);}
    private <T> com.github.pagehelper.Page<T> page(int pageNum,int pageSize,java.util.function.Supplier<List<T>> query) {
        if(pageNum<1||pageSize<1||pageSize>100)throw new FoundationException(ErrorCode.BAD_REQUEST);
        var rows=unit.run(()->{com.github.pagehelper.PageHelper.startPage(pageNum,pageSize);try{return query.get();}finally{com.github.pagehelper.PageHelper.clearPage();}});
        if(!(rows instanceof com.github.pagehelper.Page<?>))throw new IllegalStateException("Platform pagination interceptor required");
        return (com.github.pagehelper.Page<T>)rows;
    }
    private static <T,R> com.github.pagehelper.Page<R> mapPage(com.github.pagehelper.Page<T> source,java.util.function.Function<T,R> view) {
        var result=new com.github.pagehelper.Page<R>(source.getPageNum(),source.getPageSize());result.setTotal(source.getTotal());source.forEach(row->result.add(view.apply(row)));return result;
    }
    public synchronized List<DatasourceView> pageSources(int pageNum,int pageSize) {
        var rows=page(pageNum,pageSize,sources::all);var bindings=new HashMap<Long,ManagedFactory>();
        if(!rows.isEmpty())persistence.readOnly(s->s.mapper(ManagedFactoryMapper.class).byDatasources(rows.stream().map(ManagedDatasource::datasourceId).toList())).forEach(f->bindings.put(f.datasourceId(),f));
        return mapPage(rows,row->view(row,bindings.get(row.datasourceId())));
    }
    public List<FactoryView> pageFactories(int pageNum,int pageSize) {
        var rows=page(pageNum,pageSize,factories::all);var ids=rows.stream().map(ManagedFactory::datasourceId).filter(Objects::nonNull).distinct().toList();
        var names=new HashMap<Long,String>();if(!ids.isEmpty())persistence.readOnly(s->s.mapper(ManagedDatasourceMapper.class).byIds(ids)).forEach(d->names.put(d.datasourceId(),d.datasourceName()));
        return mapPage(rows,f->view(f,names.get(f.datasourceId())));
    }
    public synchronized List<DatasourceView> list(int offset,int limit){bounds(offset,limit);
        var rows=persistence.readOnly(s->s.mapper(ManagedDatasourceMapper.class).list(offset,limit));
        if(rows.isEmpty())return List.of();
        var bindings=persistence.readOnly(s->s.mapper(ManagedFactoryMapper.class).byDatasources(rows.stream().map(ManagedDatasource::datasourceId).toList()));
        var bySource=new HashMap<Long,ManagedFactory>();bindings.forEach(f->bySource.put(f.datasourceId(),f));
        return rows.stream().map(row->view(row,bySource.get(row.datasourceId()))).toList();}
    public synchronized DatasourceView detail(long id){ManagedDatasource row=persistence.readOnly(s->required(s.mapper(ManagedDatasourceMapper.class).find(id)));return view(row,persistence.readOnly(s->s.mapper(ManagedFactoryMapper.class).byDatasource(id)));}
    public DatasourceView save(DatasourceForm form,boolean creating){
        if(form.password()!=null&&form.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>512)throw new FoundationException(ErrorCode.BAD_REQUEST);
        long id=unit.run(()->{
            ManagedDatasource old=null;
            if(creating){if(form.datasourceId()!=null)throw new FoundationException(ErrorCode.BAD_REQUEST);}
            else{if(form.datasourceId()==null||form.revision()==null)throw new FoundationException(ErrorCode.BAD_REQUEST);old=locked(form.datasourceId());editable(old);if(old.revision()!=form.revision())throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);}
            String password=form.password()==null?(old==null?null:old.passwordCipher()):cipher.encrypt(form.password());
            if(password==null)throw new FoundationException(ErrorCode.BAD_REQUEST);
            var row=new ManagedDatasource(form.datasourceId(),form.datasourceName(),form.dbHost(),form.dbPort(),form.dbName(),form.dbUsername(),password,"DISABLED",old==null?1:old.revision());
            if(creating){changed(sources.insert(row));return sources.insertedId();}
            changed(sources.update(row));unit.afterCommit(()->retire(row.datasourceId()));return row.datasourceId();
        });return detail(id);
    }
    public DatasourceView status(long id,boolean enabled){
        unit.run(()->{
            var old=locked(id);
            if(!enabled&&factories.bound(id)!=0)throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            String next=enabled?"ENABLED":"DISABLED";
            if(!next.equals(old.status())){
                changed(sources.update(new ManagedDatasource(id,old.datasourceName(),old.dbHost(),old.dbPort(),old.dbName(),old.dbUsername(),old.passwordCipher(),next,old.revision())));
                unit.afterCommit(()->retire(id));
            }return null;
        });return detail(id);
    }
    public void delete(long id){unit.run(()->{var row=locked(id);editable(row);changed(sources.delete(id,row.revision()));unit.afterCommit(()->retire(id));return null;});}
    public List<FactoryView> factoryList(int offset,int limit){bounds(offset,limit);
        var rows=persistence.readOnly(s->s.mapper(ManagedFactoryMapper.class).list(offset,limit));
        var ids=rows.stream().map(ManagedFactory::datasourceId).filter(Objects::nonNull).distinct().toList();
        var names=new HashMap<Long,String>();
        if(!ids.isEmpty())persistence.readOnly(s->s.mapper(ManagedDatasourceMapper.class).byIds(ids)).forEach(d->names.put(d.datasourceId(),d.datasourceName()));
        return rows.stream().map(f->view(f,names.get(f.datasourceId()))).toList();
    }
    public FactoryView factory(String id){var f=factoryRecord(id);String name=f.datasourceId()==null?null:persistence.readOnly(s->required(s.mapper(ManagedDatasourceMapper.class).find(f.datasourceId()))).datasourceName();return view(f,name);}
    private ManagedFactory factoryRecord(String id){return persistence.readOnly(s->required(s.mapper(ManagedFactoryMapper.class).find(id)));}
    public FactoryView saveFactory(FactoryForm form,boolean creating){
        unit.run(()->{
            // Serialize competing binds and datasource disable through the datasource row lock.
            if(form.datasourceId()!=null&& !"ENABLED".equals(locked(form.datasourceId()).status()))throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            var old=factories.find(form.factoryId());
            if(creating&&old!=null)throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if(!creating){required(old);if(form.revision()==null||old.revision()!=form.revision())throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);}
            var row=new ManagedFactory(form.factoryId(),form.enterpriseId(),form.factoryCode(),form.factoryName(),form.datasourceId(),form.status(),creating?1:old.revision());
            if(creating)changed(factories.insert(row));else changed(factories.update(row));
            if(old!=null&&old.datasourceId()!=null)unit.afterCommit(()->retire(old.datasourceId()));
            return null;
        });return factory(form.factoryId());
    }
    public void deleteFactory(String id,long revision){unit.run(()->{
        var old=required(factories.find(id));
        if(factories.roles(id)!=0||factories.jobs(id)!=0||factories.syncStates(id)!=0)throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        changed(factories.delete(id,revision));if(old.datasourceId()!=null)unit.afterCommit(()->retire(old.datasourceId()));return null;
    });}
    public Optional<Factory> find(FactoryId id){return persistence.readOnly(s->Optional.ofNullable(s.mapper(ManagedFactoryMapper.class).find(id.value()))).map(ManagedDatasourceService::core);}
    public List<Factory> enabled(int offset,int limit){bounds(offset,limit);return persistence.readOnly(s->s.mapper(ManagedFactoryMapper.class).enabled(offset,limit)).stream().map(ManagedDatasourceService::core).toList();}
    private static Factory core(ManagedFactory f){return new Factory(new FactoryId(f.factoryId()),f.enterpriseId(),f.factoryCode(),f.factoryName(),FactoryStatus.valueOf(f.status()));}
    public record DdlView(String sql,String fileName) {}
    public DdlView ddl(long id,boolean refresh){
        var row=persistence.readOnly(s->required(s.mapper(ManagedDatasourceMapper.class).find(id)));
        if(!"ENABLED".equals(row.status()))throw new FoundationException(ErrorCode.RUNTIME_NOT_CONFIGURED);
        var cached=ddlCache.get(id);if(!refresh&&cached!=null&&cached.revision()==row.revision())return cached.view();
        var file=ddlRoot.resolve("datasource-"+id+"-r"+row.revision()+".sql");
        if(!refresh&&java.nio.file.Files.isRegularFile(file)) {
            try {
                if(java.nio.file.Files.size(file)>16*1024*1024)throw new FoundationException(ErrorCode.UNPROCESSABLE);
                var result=new DdlView(java.nio.file.Files.readString(file),file.getFileName().toString());
                ddlCache.put(id,new DdlCache(row.revision(),java.nio.file.Files.getLastModifiedTime(file).toInstant(),result));return result;
            }catch(java.io.IOException ex){throw new IllegalStateException("DDL snapshot read failed",ex);}
        }
        var pool=DruidPools.create(settings,"ddl-managed-"+id,row.jdbcUrl(),row.dbUsername(),cipher.decrypt(row.passwordCipher()));
        try {
            if(physicalIdentity(pool).equals(physicalIdentity(platform)))throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            var definitions=new DatasourceDdlExtraMapper().selectTableDefinitions(pool);
            if(definitions.size()>1000)throw new FoundationException(ErrorCode.UNPROCESSABLE);
            String sql=DatasourceDdlWriter.write(definitions);
            if(sql.length()>16*1024*1024)throw new FoundationException(ErrorCode.UNPROCESSABLE);
            try {
                java.nio.file.Files.createDirectories(ddlRoot);
                java.nio.file.Files.setPosixFilePermissions(ddlRoot,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
                var temporary=java.nio.file.Files.createTempFile(ddlRoot,"snapshot-",".sql");
                try {java.nio.file.Files.writeString(temporary,sql);java.nio.file.Files.setPosixFilePermissions(temporary,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));java.nio.file.Files.move(temporary,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}
                finally {java.nio.file.Files.deleteIfExists(temporary);}
            }catch(java.io.IOException ex){throw new IllegalStateException("DDL snapshot write failed",ex);}
            var result=new DdlView(sql,"datasource-"+id+"-r"+row.revision()+".sql");ddlCache.put(id,new DdlCache(row.revision(),java.time.Instant.now(),result));return result;
        }catch(java.sql.SQLException ex){throw new FoundationException(ErrorCode.RUNTIME_NOT_CONFIGURED,ex);}finally{pool.close();}
    }
    private static void bounds(int offset,int limit){if(offset<0||limit<1||limit>100)throw new FoundationException(ErrorCode.BAD_REQUEST);}
    public synchronized BusinessRuntimeBinding resolve(FactoryScope scope){
        var f=factoryRecord(scope.factoryId().value());
        if(!"ENABLED".equals(f.status()))throw new FoundationException(ErrorCode.FACTORY_DISABLED);
        if(f.datasourceId()==null)throw new FoundationException(ErrorCode.RUNTIME_NOT_CONFIGURED);
        var source=persistence.readOnly(s->required(s.mapper(ManagedDatasourceMapper.class).find(f.datasourceId())));
        if(!"ENABLED".equals(source.status()))throw new FoundationException(ErrorCode.RUNTIME_NOT_CONFIGURED);
        var binding=new BusinessRuntimeBinding(scope.factoryId(),identity(source.datasourceId()),source.revision());
        if(!registry.contains(binding)){
            // A version change during an active business unit must fail instead of loading another pool.
            if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new FoundationException(ErrorCode.CONFLICT);
            var pool=DruidPools.create(settings,"business-managed-"+source.datasourceId(),source.jdbcUrl(),source.dbUsername(),cipher.decrypt(source.passwordCipher()));
            boolean transferred=false;
            try{
                String database=physicalIdentity(pool);
                if(database.equals(physicalIdentity(platform))||physical.entrySet().stream().anyMatch(e->!e.getKey().equals(binding.datasourceIdentity())&&e.getValue().equals(database)))throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
                registry.retire(binding.datasourceIdentity());
                registry.registerDedicated(binding.factoryId(),binding.datasourceIdentity(),binding.revision(),pool,pool::close,mappers);transferred=true;
                physical.put(binding.datasourceIdentity(),database);
            }finally{if(!transferred)pool.close();}
        }
        return binding;
    }
    private synchronized void retire(long id){registry.retire(identity(id));physical.remove(identity(id));}
    private static String physicalIdentity(DataSource ds){
        try(var c=ds.getConnection();var st=c.prepareStatement("SELECT @@server_uuid,DATABASE()");var rs=st.executeQuery()){
            if(!rs.next()||rs.getString(1)==null||rs.getString(2)==null)throw new FoundationException(ErrorCode.RUNTIME_NOT_CONFIGURED);
            return rs.getString(1)+"/"+rs.getString(2);
        }catch(java.sql.SQLException ex){throw new FoundationException(ErrorCode.RUNTIME_NOT_CONFIGURED,ex);}
    }
}
