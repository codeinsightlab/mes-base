package dev.mesbase.framework.websocket;
import dev.mesbase.common.core.domain.MessageEvents;
import dev.mesbase.framework.web.service.TokenService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.*;
/** Source WebSocketServer/WebSocketUsers PORT: authenticated receive-only streaming.
 * Credentials stay in Authorization, sender/recipient come from the persisted message.
 * No anonymous relay, token URL, arbitrary client recipient or message-body logging. */
@org.springframework.stereotype.Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public final class MessageEventHub implements MessageEvents, AutoCloseable {
 private record Connection(long userId,String credential,SseEmitter emitter){}
 private final ConcurrentMap<SseEmitter,Connection> connections=new ConcurrentHashMap<>();
 private final ScheduledExecutorService heartbeat=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"inbox-heartbeat");t.setDaemon(true);return t;});
 private final TokenService tokens;
 public MessageEventHub(TokenService tokens){this.tokens=tokens;heartbeat.scheduleWithFixedDelay(this::heartbeat,15,15,TimeUnit.SECONDS);}
 public SseEmitter connect(String credential){var user=tokens.resolve(credential);if(connections.size()>=100)throw new dev.mesbase.common.exception.ServiceException("在线消息连接数量已达上限");var emitter=new SseEmitter(300_000L);var connection=new Connection(user.getUserId(),credential,emitter);connections.put(emitter,connection);emitter.onCompletion(()->connections.remove(emitter));emitter.onTimeout(()->{connections.remove(emitter);emitter.complete();});emitter.onError(failure->connections.remove(emitter));send(connection,"connected");return emitter;}
 public void changed(long recipientId){for(var c:connections.values())if(c.userId()==recipientId)send(c,"inbox-changed");}
 private void heartbeat(){for(var c:connections.values())send(c,"heartbeat");}
 private void send(Connection c,String event){try{var fresh=tokens.resolve(c.credential());if(fresh.getUserId()!=c.userId())throw new IllegalStateException("Session changed");c.emitter().send(SseEmitter.event().name(event).data("refresh"));}catch(Exception failure){connections.remove(c.emitter());c.emitter().complete();}}
 public void close(){heartbeat.shutdownNow();for(var c:connections.values())c.emitter().complete();connections.clear();}
}
