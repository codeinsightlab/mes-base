package dev.mesbase.common.utils.html;
import org.jsoup.Jsoup;import org.jsoup.safety.*;import org.jsoup.nodes.*;
/** SOURCE_PRODUCT_PORT: retain rich-text formatting while parsing hostile markup safely. */
public final class RichTextSanitizer {
 public static String clean(String input){if(input==null||!input.contains("<"))return input;
  var safe=Safelist.basic().addTags("p","div","span","s","u","h1","h2","h3","h4","h5","h6","pre","img","video").addAttributes(":all","class","style").addAttributes("li","data-list").addAttributes("img","src","alt","data-file-id","data-resource").addAttributes("video","src","controls","data-file-id","data-resource");
  Document doc=new Cleaner(safe).clean(Jsoup.parseBodyFragment(input));doc.outputSettings().prettyPrint(false);
  for(Element node:doc.getAllElements()){
   if(node.hasAttr("class")){String classes=java.util.Arrays.stream(node.className().split("\\s+")).filter(v->v.matches("ql-[a-z0-9_-]+")).collect(java.util.stream.Collectors.joining(" "));node.attr("class",classes);}
   if(node.hasAttr("style")){String style=java.util.Arrays.stream(node.attr("style").split(";")).map(String::trim).filter(v->v.matches("(?i)(color|background-color):\\s*(#[0-9a-f]{3,8}|rgb\\(\\s*[0-9]{1,3}\\s*,\\s*[0-9]{1,3}\\s*,\\s*[0-9]{1,3}\\s*\\)|[a-z]{1,20})")).collect(java.util.stream.Collectors.joining(";"));node.attr("style",style);}
   if(node.tagName().equals("img")||node.tagName().equals("video")){String id=node.attr("data-file-id");if(!id.matches("[a-f0-9-]{36}")||!node.attr("data-resource").equals("notice-media")){node.remove();continue;}node.attr("src","/api/platform/files/"+id+"/content?resource=notice-media");if(node.tagName().equals("video"))node.attr("controls","");}
  }
  return doc.body().html();
 }
 private RichTextSanitizer(){}
}
