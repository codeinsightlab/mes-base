package dev.mesbase.platform.security;
import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import java.util.List;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import dev.mesbase.platform.management.SessionService;
import dev.mesbase.platform.web.ErrorWriter;
import dev.mesbase.core.error.FoundationException;
/** Identity-only Bearer transport. Invalid Bearer never falls back to Basic or anonymous. */
public final class PlatformTokenFilter extends OncePerRequestFilter {
 private final SessionService sessions;public PlatformTokenFilter(SessionService sessions){this.sessions=sessions;}
 protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  String header=req.getHeader("Authorization");
  if(header!=null && header.regionMatches(true,0,"Bearer ",0,7)){
   try{String username=sessions.authenticate(header.substring(7));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username,null,List.of()));}
   catch(FoundationException failure){ErrorWriter.write(failure.code(),req,res);return;}
  }
  chain.doFilter(req,res);
 }
}
