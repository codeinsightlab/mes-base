package dev.mesbase.web.controller.system;

import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.framework.web.service.CaptchaService;
import dev.mesbase.platform.web.EndpointAccess;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SystemCaptchaController {
    private final CaptchaService captcha;

    public SystemCaptchaController(CaptchaService captcha) {
        this.captcha = captcha;
    }

    @GetMapping("/api/login-options")
    @EndpointAccess(EndpointAccess.Kind.PUBLIC)
    public CaptchaService.LoginOptions options() {
        return captcha.options();
    }

    @GetMapping("/api/captchaImage")
    @EndpointAccess(EndpointAccess.Kind.PUBLIC)
    public AjaxResult image() throws IOException {
        return captcha.image();
    }
}
