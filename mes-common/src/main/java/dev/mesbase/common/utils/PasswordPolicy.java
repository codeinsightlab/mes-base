package dev.mesbase.common.utils;

import dev.mesbase.common.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.env.Environment;

/** YAML consumer for Source BCrypt credentials. Never silently truncate UTF-8 input. */
@org.springframework.stereotype.Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public final class PasswordPolicy {
    public record View(int minLength, int maxLength, int maxBytes) {}
    private final View view;
    public PasswordPolicy(Environment environment) {
        int min=environment.getProperty("user.password.min-length",Integer.class,12);
        int max=environment.getProperty("user.password.max-length",Integer.class,128);
        if(min<5 || min>72 || max<min || max>128)throw new IllegalArgumentException("Password length policy invalid");
        view=new View(min,max,72);
    }
    public View view(){return view;}
    public void validate(String raw) {
        if(raw==null || raw.length()<view.minLength() || raw.length()>view.maxLength())
            throw new ServiceException("密码长度必须介于"+view.minLength()+"和"+view.maxLength()+"之间");
        if(raw.getBytes(StandardCharsets.UTF_8).length>view.maxBytes())
            throw new ServiceException("密码UTF-8长度不能超过72字节");
    }
    public String encode(String raw){validate(raw);return SecurityUtils.encryptPassword(raw);}
}
