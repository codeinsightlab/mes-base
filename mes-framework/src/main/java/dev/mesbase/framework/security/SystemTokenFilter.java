package dev.mesbase.framework.security;
import dev.mesbase.framework.web.service.TokenService;import jakarta.servlet.*;import jakarta.servlet.http.*;import org.springframework.web.filter.OncePerRequestFilter;
public final class SystemTokenFilter extends OncePerRequestFilter {
 private final TokenService tokens;public SystemTokenFilter(TokenService tokens){this.tokens=tokens;}
 @Override protected boolean shouldNotFilter(HttpServletRequest request){return dev.mesbase.framework.openapi.OpenApiHttp.isOpenApi(request);}
 @Override protected boolean shouldNotFilterAsyncDispatch(){return false;}
 protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws java.io.IOException,ServletException {
  request.setAttribute("mes.authScheme", "Bearer");
  try{var user=tokens.getLoginUser(request);if(user!=null){var auth=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(user,null,java.util.List.of());org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);}}
  catch(dev.mesbase.core.error.FoundationException failure){dev.mesbase.platform.web.ErrorWriter.write(failure.code(),request,response);return;}
  chain.doFilter(request,response);
 }
}
