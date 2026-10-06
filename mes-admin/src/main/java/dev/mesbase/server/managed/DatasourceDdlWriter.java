package dev.mesbase.server.managed;

import java.time.Instant;
import java.util.*;
import java.util.regex.*;

/** 保留 SHOW CREATE 的原始方言，避免 SQL 格式化器重写生成列/索引语法。 */
public final class DatasourceDdlWriter {
    private static final Pattern FOREIGN_KEY = Pattern.compile("(?is)^\\s*(?:CONSTRAINT\\s+(?:`(?:``|[^`])+`|[a-zA-Z0-9_]+)\\s+)?FOREIGN\\s+KEY\\b");
    private static final Pattern COUNTER = Pattern.compile("(?i)AUTO_INCREMENT\\s*=\\s*[0-9]+\\b");
    private DatasourceDdlWriter() { }
    public static String write(List<String> definitions) {
        StringBuilder sql = new StringBuilder("-- MES business schema snapshot\n-- Generated at: "+Instant.now()+"\n"
                +"-- Tables: "+definitions.size()+"\n"
                +"-- Scope: tables, columns, primary/unique/normal indexes and table options.\n"
                +"-- Excluded: data, foreign keys, views, triggers, routines and events.\n"
                +"-- Run in an EMPTY target database. No CREATE DATABASE, USE or DROP is included.\n"
                +"-- Not a transactionally consistent snapshot during concurrent schema changes.\n\n");
        sql.append("SET @MES_DDL_OLD_SQL_MODE=@@SESSION.SQL_MODE;\nSET SESSION SQL_MODE='NO_AUTO_VALUE_ON_ZERO';\n\n");
        for (String definition : definitions) sql.append(tablesAndIndexes(definition)).append(";\n\n");
        return sql.append("SET SESSION SQL_MODE=@MES_DDL_OLD_SQL_MODE;\n").toString();
    }
    static String tablesAndIndexes(String ddl) {
        if (!ddl.startsWith("CREATE TABLE ")) throw new IllegalArgumentException("Unsupported SHOW CREATE result");
        int depth=0,open=-1,start=-1,close=-1;
        List<String> elements=new ArrayList<>();
        for(int i=0;i<ddl.length();i++) {
            int skipped=quotedOrCommentEnd(ddl,i);
            if(skipped!=i){i=skipped;continue;}
            char c=ddl.charAt(i);
            if(c=='('){if(depth++==0){open=i;start=i+1;}}
            else if(c==')'){
                if(--depth==0){elements.add(ddl.substring(start,i));close=i;break;}
            } else if(c==',' && depth==1){elements.add(ddl.substring(start,i));start=i+1;}
        }
        if(open<0 || close<0)throw new IllegalArgumentException("Incomplete SHOW CREATE result");
        elements.removeIf(element -> FOREIGN_KEY.matcher(element).find());
        String options=ddl.substring(close+1);
        StringBuilder cleaned=new StringBuilder();
        for(int i=0;i<options.length();i++) {
            int end=quotedOrCommentEnd(options,i);
            if(end!=i){cleaned.append(options,i,end+1);i=end;continue;}
            Matcher counter=COUNTER.matcher(options);counter.region(i,options.length());
            if((i==0 || Character.isWhitespace(options.charAt(i-1))) && counter.lookingAt()){i=counter.end()-1;continue;}
            cleaned.append(options.charAt(i));
        }
        return ddl.substring(0,open+1)+String.join(",",elements)+")"+cleaned;
    }
    /** 跳过字符串、标识符和注释；只在语法顶层处理分隔符。 */
    private static int quotedOrCommentEnd(String text,int start) {
        char quote=text.charAt(start);
        if(quote=='\'' || quote=='"' || quote=='`') {
            for(int i=start+1;i<text.length();i++) {
                char c=text.charAt(i);
                if(c=='\\' && quote!='`'){i++;continue;}
                if(c==quote){if(i+1<text.length() && text.charAt(i+1)==quote){i++;continue;}return i;}
            }
            throw new IllegalArgumentException("Unterminated quoted SQL");
        }
        if(text.startsWith("/*",start)){
            int end=text.indexOf("*/",start+2);if(end<0)throw new IllegalArgumentException("Unterminated SQL comment");return end+1;
        }
        if(quote=='#' || (text.startsWith("--",start) && start+2<text.length() && Character.isWhitespace(text.charAt(start+2)))) {
            int end=text.indexOf('\n',start);return end<0?text.length()-1:end;
        }
        return start;
    }
}
