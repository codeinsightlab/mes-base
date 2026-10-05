package dev.mesbase.platform.management;
import java.security.*;
import java.time.*;
import java.util.*;
import dev.mesbase.core.error.*;
import dev.mesbase.infrastructure.platform.PlatformRows.*;
import dev.mesbase.platform.management.PlatformContracts.*;
import org.springframework.security.crypto.password.PasswordEncoder;
/** Opaque revocable identity-only sessions; no Factory, roles or permissions are cached in tokens. */
public final class SessionService {
 private final PlatformStore store;private final PasswordEncoder encoder;private final Clock clock;private final Duration ttl;
 private final String dummyHash;
 public SessionService(PlatformStore store,PasswordEncoder encoder,Clock clock,Duration ttl){if(ttl.isNegative()||ttl.isZero()||ttl.compareTo(Duration.ofDays(1))>0)throw new IllegalArgumentException("Session TTL must be within one day");this.store=store;this.encoder=encoder;this.clock=clock;this.ttl=ttl;dummyHash=encoder.encode(UUID.randomUUID().toString());}
 static String hash(String raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private LocalDateTime now(){return LocalDateTime.now(clock);}
 public SessionView login(Login input,String requestId){
  var user=store.run(m->m.userByUsername(input.username()));boolean matches=encoder.matches(input.password(),user==null?dummyHash:user.passwordHash());
  boolean valid=matches && user!=null && user.status()==Status.ENABLED;
  store.run(m->{m.insertLoginLog(new LoginLog(UUID.randomUUID().toString(),requestId,user==null?"UNKNOWN":user.id(),null,JobScope.PLATFORM,valid?"SUCCEEDED":"REJECTED",now()));return null;});
  if(!valid)throw new FoundationException(ErrorCode.UNAUTHENTICATED);
  byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);LocalDateTime expires=now().plus(ttl);
  store.run(m->{m.insertSession(new Session(hash(raw),user.id(),expires));return null;});return new SessionView(raw,expires);
 }
 public String authenticate(String raw){if(raw==null||!raw.matches("[A-Za-z0-9_-]{43}"))throw new FoundationException(ErrorCode.UNAUTHENTICATED);return store.run(m->{var s=m.findSession(hash(raw));if(s==null||!s.expiresAt().isAfter(now()))throw new FoundationException(ErrorCode.UNAUTHENTICATED);var u=m.findUser(s.userId());if(u==null||u.status()!=Status.ENABLED)throw new FoundationException(ErrorCode.UNAUTHENTICATED);return u.username();});}
 public void logout(String raw){authenticate(raw);store.run(m->{m.revoke(hash(raw));return null;});}
}
