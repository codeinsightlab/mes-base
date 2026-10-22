package dev.mesbase.framework.web.service;

import java.util.Optional;
import dev.mesbase.common.utils.SecurityUtils;import dev.mesbase.common.core.domain.model.LoginUser;import dev.mesbase.system.service.*;import dev.mesbase.core.error.*;import jakarta.servlet.http.HttpServletRequest;import org.springframework.core.env.Environment;import org.springframework.data.redis.core.StringRedisTemplate;
/** Source login/password retry PORT. Identity, lock, captcha and audit are server-owned. */
@org.springframework.stereotype.Service @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SysLoginService {
 private final dev.mesbase.common.core.redis.SystemCacheNamespace namespace;private final ISysUserService users;private final ISysLogininforService logs;private final TokenService tokens;private final StringRedisTemplate redis;private final CaptchaService captcha;private final int maxRetries,lockMinutes;private final String dummy=SecurityUtils.encryptPassword(java.util.UUID.randomUUID().toString());
 public SysLoginService(ISysUserService users,ISysLogininforService logs,TokenService tokens,StringRedisTemplate redis,CaptchaService captcha,Environment env,dev.mesbase.common.core.redis.SystemCacheNamespace namespace){this.namespace=namespace;this.users=users;this.logs=logs;this.tokens=tokens;this.redis=redis;this.captcha=captcha;maxRetries=env.getProperty("user.password.max-retry-count",Integer.class,5);lockMinutes=env.getProperty("user.password.lock-time",Integer.class,10);if(maxRetries<1||maxRetries>20||lockMinutes<1||lockMinutes>1440)throw new IllegalArgumentException("Password lock invalid");}
 public record Session(String accessToken,java.time.Instant expiresAt){}
 public Session login(String username,String password,String code,String uuid,HttpServletRequest request){captcha.validate(uuid,code);
  var user=users.selectUserByUserName(username);
  // The database collation can resolve multiple input spellings to one user.
  // Existing accounts must share the canonical persisted retry identity.
  String canonical=user==null?username:user.getUserName();String key=namespace.key("mes:system:retry:"+TokenService.hash(canonical));String current=redis.opsForValue().get(key);if(current!=null&&Integer.parseInt(current)>=maxRetries)throw denied();
  boolean correct=SecurityUtils.matchesPassword(password,user==null?dummy:user.getPassword());boolean valid=user!=null&&"0".equals(user.getStatus())&&"0".equals(user.getDelFlag())&&correct;
  var log=new dev.mesbase.system.domain.SysLogininfor();log.setUserName(username);log.setIpaddr(request.getRemoteAddr());var agent=ClientAgent.parse(request.getHeader("User-Agent"));log.setBrowser(agent.browser());log.setOs(agent.os());log.setStatus(valid?"0":"1");log.setMsg(valid?"登录成功":"账号或密码错误");log.setRequestId(String.valueOf(request.getAttribute("mes.requestId")));log.setActorId(Optional.ofNullable(user).map(nullableValue -> nullableValue.getUserId().toString()).orElse(null));logs.insertLogininfor(log);
  if(!valid){var script=new org.springframework.data.redis.core.script.DefaultRedisScript<Long>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n",Long.class);redis.execute(script,java.util.List.of(key),Integer.toString(lockMinutes*60));throw denied();}redis.delete(key);
  var login=new LoginUser(user.getUserId(),user.getDeptId(),user,java.util.Set.of());return new Session(tokens.createToken(login,request),java.time.Instant.now().plusSeconds(tokens.ttlMinutes()*60L));
 }
 private RuntimeException denied(){return new FoundationException(ErrorCode.UNAUTHENTICATED);}
}
