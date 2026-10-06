package dev;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication(scanBasePackages = "dev.mesbase")
public class MesBaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(MesBaseApplication.class, args);
    }
}
