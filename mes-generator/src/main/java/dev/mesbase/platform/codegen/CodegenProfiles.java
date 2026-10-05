package dev.mesbase.platform.codegen;
import javax.sql.DataSource;import java.util.Map;import java.util.Objects;
/** Datasources are injected developer profiles, not Factory/runtime bindings. */
public final class CodegenProfiles {
 private final String defaultProfile;private final Map<String,DataSource> profiles;
 public CodegenProfiles(String defaultProfile,Map<String,DataSource> profiles){this.defaultProfile=defaultProfile;this.profiles=Map.copyOf(profiles);}
 public String name(String requested){String name=requested==null?defaultProfile:requested;if(name==null||!profiles.containsKey(name))throw new CodegenException(CodegenException.Code.UNKNOWN_DATASOURCE);return name;}
 public DataSource datasource(String name){return Objects.requireNonNull(profiles.get(name));}
}
