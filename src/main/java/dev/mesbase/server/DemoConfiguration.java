package dev.mesbase.server;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;
import dev.mesbase.core.factory.*;
import dev.mesbase.core.security.*;
import dev.mesbase.core.security.Role;
import dev.mesbase.infrastructure.catalog.*;
import dev.mesbase.infrastructure.security.*;
/** Explicit synthetic local demo. No business tables, external addresses or default passwords. */
@Configuration @Profile("demo")
public class DemoConfiguration {
    @Bean FactoryCatalog demoFactories() { return new InMemoryFactoryCatalog(List.of(
        new Factory(new FactoryId("A"),"demo-enterprise","DEMO_A","Synthetic Factory A",FactoryStatus.ENABLED),
        new Factory(new FactoryId("B"),"demo-enterprise","DEMO_B","Synthetic Factory B",FactoryStatus.ENABLED))); }
    @Bean AuthorizationService demoAuthorization() {
        var service=new InMemoryRoleAuthorization(); service.replace(new AuthenticatedActor("reader"),List.of(new Role("demo-reader-A",new FactoryId("A"),Set.of(new Permission("foundation:access:read"),new Permission("foundation:erp:inspect"))))); return service;
    }
    @Bean UserDetailsService demoUsers(Environment env) {
        String password=env.getProperty("MES_DEMO_PASSWORD");
        if(password==null || password.length()<12) throw new IllegalStateException("Demo requires a locally supplied MES_DEMO_PASSWORD of at least 12 characters");
        String encoded="{bcrypt}"+new BCryptPasswordEncoder().encode(password);
        return new InMemoryUserDetailsManager(User.withUsername("visitor").password(encoded).authorities(List.of()).build(),User.withUsername("reader").password(encoded).authorities(List.of()).build());
    }
}
