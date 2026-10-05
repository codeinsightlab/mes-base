package dev.mesbase.server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@org.springframework.context.annotation.EnableAspectJAutoProxy(exposeProxy=true)
@SpringBootApplication(scanBasePackages = "dev.mesbase")
public class MesBaseApplication {
    public static void main(String[] args) { SpringApplication.run(MesBaseApplication.class, args); }
}
