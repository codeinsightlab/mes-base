package dev.mesbase.framework.web;
@org.springframework.web.bind.annotation.RestControllerAdvice(basePackages="dev.mesbase.web.controller")
public final class SystemResponseAdvice implements org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice<Object> {
 public boolean supports(org.springframework.core.MethodParameter method,Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> converter){return true;}
 public Object beforeBodyWrite(Object body,org.springframework.core.MethodParameter method,org.springframework.http.MediaType type,Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> converter,org.springframework.http.server.ServerHttpRequest request,org.springframework.http.server.ServerHttpResponse response){
  if(body instanceof dev.mesbase.common.core.page.TableDataInfo table)table.setRows((java.util.List<?>)dev.mesbase.system.dto.SystemViews.project(table.getRows()));
  return dev.mesbase.system.dto.SystemViews.project(body);
 }
}