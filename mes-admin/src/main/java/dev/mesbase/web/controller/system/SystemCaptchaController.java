package dev.mesbase.web.controller.system;
@org.springframework.web.bind.annotation.RestController @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SystemCaptchaController {private final dev.mesbase.framework.web.service.CaptchaService captcha;public SystemCaptchaController(dev.mesbase.framework.web.service.CaptchaService captcha){this.captcha=captcha;}
 @org.springframework.web.bind.annotation.GetMapping("/api/captchaImage") @dev.mesbase.platform.web.EndpointAccess(dev.mesbase.platform.web.EndpointAccess.Kind.PUBLIC) public dev.mesbase.common.core.domain.AjaxResult image()throws java.io.IOException{return captcha.image();}}
