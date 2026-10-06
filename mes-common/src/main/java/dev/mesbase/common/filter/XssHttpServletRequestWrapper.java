package dev.mesbase.common.filter;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.utils.html.EscapeUtil;

import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * XSS过滤处理
 *
 * @author ruoyi
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper
{
    /**
     * @param request
     */
    public XssHttpServletRequestWrapper(HttpServletRequest request)
    {
        super(request);
    }

    @Override
    public String[] getParameterValues(String name)
    {
        String[] values = super.getParameterValues(name);
        if (values != null)
        {
            int length = values.length;
            String[] escapseValues = new String[length];
            for (int i = 0; i < length; i++)
            {
                // 防xss攻击和过滤前后空格
                escapseValues[i] = EscapeUtil.clean(values[i]).trim();
            }
            return escapseValues;
        }
        return super.getParameterValues(name);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException
    {
        // 非json类型，直接返回
        if (!isJsonRequest())
        {
            return super.getInputStream();
        }

        // 为空，直接返回
        String json = IOUtils.toString(super.getInputStream(), "utf-8");
        if (StringUtils.isEmpty(json))
        {
            return super.getInputStream();
        }

        // xss过滤
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var tree=mapper.readTree(json);cleanTree(tree);json=mapper.writeValueAsString(tree);
        byte[] jsonBytes = json.getBytes("utf-8");
        final ByteArrayInputStream bis = new ByteArrayInputStream(jsonBytes);
        return new ServletInputStream()
        {
            @Override
            public boolean isFinished()
            {
                return bis.available()==0;
            }

            @Override
            public boolean isReady()
            {
                return true;
            }

            @Override
            public int available() throws IOException
            {
                return bis.available();
            }

            @Override
            public void setReadListener(ReadListener readListener)
            {
            }

            @Override
            public int read() throws IOException
            {
                return bis.read();
            }
        };
    }

    private static void cleanTree(com.fasterxml.jackson.databind.JsonNode node){
        if(node.isObject()){
            var object=(com.fasterxml.jackson.databind.node.ObjectNode)node;var fields=new java.util.ArrayList<String>();object.fieldNames().forEachRemaining(fields::add);
            for(String key:fields){var value=object.get(key);if(java.util.Set.of("password","oldPassword","newPassword","confirmPassword").contains(key))continue;
                if(value.isTextual())object.put(key,EscapeUtil.clean(value.textValue()));else cleanTree(value);}
        }else if(node.isArray())for(int i=0;i<node.size();i++){var value=node.get(i);if(value.isTextual())((com.fasterxml.jackson.databind.node.ArrayNode)node).set(i,new com.fasterxml.jackson.databind.node.TextNode(EscapeUtil.clean(value.textValue())));else cleanTree(value);}
    }
    /**
     * 是否是Json请求
     *
     * @param request
     */
    public boolean isJsonRequest()
    {
        String header = super.getHeader(HttpHeaders.CONTENT_TYPE);
        return StringUtils.startsWithIgnoreCase(header, MediaType.APPLICATION_JSON_VALUE);
    }
}
