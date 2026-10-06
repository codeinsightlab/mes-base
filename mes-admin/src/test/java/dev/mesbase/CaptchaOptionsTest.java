package dev.mesbase;

import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.common.utils.PasswordPolicy;
import dev.mesbase.framework.web.service.CaptchaService;
import dev.mesbase.system.service.ISysConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CaptchaOptionsTest {
 @Test void disabledOptionsDoNotGenerateOrStoreCaptcha() throws Exception {
  var cache=mock(RedisCache.class);var config=mock(ISysConfigService.class);
  var service=new CaptchaService(cache,config,new MockEnvironment().withProperty("ruoyi.captcha.enabled","false"),new PasswordPolicy(new MockEnvironment()));
  assertFalse(service.options().captchaOnOff());
  assertFalse(service.image().containsKey("img"));
  verifyNoInteractions(cache);
 }
 @Test void optionsReadBothServerSwitchesWithoutCreatingAChallenge() {
  var cache=mock(RedisCache.class);var config=mock(ISysConfigService.class);
  when(config.selectCaptchaOnOff()).thenReturn(true);
  when(config.selectConfigByKey("sys.account.registerUser")).thenReturn("true");
  var service=new CaptchaService(cache,config,new MockEnvironment(),new PasswordPolicy(new MockEnvironment()));
  assertTrue(service.options().captchaOnOff());assertTrue(service.options().registrationEnabled());
  verifyNoInteractions(cache);
 }
}
