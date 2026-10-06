package dev.mesbase.platform.codegen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Generated Java follows the same explicit-import convention as hand-written Java. */
final class JavaSourceImports {
    private static final Pattern TYPE = Pattern.compile("(\"(?:\\\\.|[^\"\\\\])*\"|//[^\\n]*|/\\*[\\s\\S]*?\\*/)|(\\b(?:[a-z][\\w]*\\.)+[A-Z][\\w]*)");
    private JavaSourceImports() {}
    static String explicit(String body) {
        Map<String,String> imports = new LinkedHashMap<>();
        Matcher matcher = TYPE.matcher(body);
        StringBuilder code = new StringBuilder();
        while (matcher.find()) {
            if (matcher.group(1) != null) { matcher.appendReplacement(code,Matcher.quoteReplacement(matcher.group())); continue; }
            String name=matcher.group(2);
            String simple=name.substring(name.lastIndexOf('.')+1);
            String previous=imports.putIfAbsent(simple,name);
            matcher.appendReplacement(code, Matcher.quoteReplacement(previous==null || previous.equals(name) ? simple : name));
        }
        matcher.appendTail(code);
        StringBuilder result=new StringBuilder();
        imports.values().stream().sorted().forEach(name -> result.append("import ").append(name).append(";\n"));
        return result.append('\n').append(code).toString();
    }
}
