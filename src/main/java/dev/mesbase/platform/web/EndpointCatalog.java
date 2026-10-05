package dev.mesbase.platform.web;
import org.springframework.web.method.HandlerMethod;
import dev.mesbase.core.security.Permission;
public final class EndpointCatalog {
    private EndpointCatalog() {}
    public static EndpointAccess required(HandlerMethod handler) {
        EndpointAccess access=handler.getMethodAnnotation(EndpointAccess.class);
        if(access==null) throw new IllegalStateException("HTTP endpoint must explicitly declare access: "+handler.getMethod().getName());
        if(access.value()==EndpointAccess.Kind.AUTHORIZED) new Permission(access.permission());
        else if(!access.permission().isEmpty()) throw new IllegalStateException("Permission only applies to AUTHORIZED endpoints");
        return access;
    }
}
