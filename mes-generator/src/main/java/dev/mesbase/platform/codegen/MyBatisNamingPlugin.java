package dev.mesbase.platform.codegen;

import java.util.List;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.PluginAdapter;
import org.mybatis.generator.api.dom.java.Interface;
import org.mybatis.generator.api.dom.java.Method;
import org.mybatis.generator.api.dom.java.TopLevelClass;
import org.mybatis.generator.api.dom.xml.Attribute;
import org.mybatis.generator.api.dom.xml.Document;
import org.mybatis.generator.api.dom.xml.TextElement;
import org.mybatis.generator.api.dom.xml.XmlElement;

/**
 * Adapts single-table scaffolding without business fields or runtime registrations.
 */
public final class MyBatisNamingPlugin extends PluginAdapter {
    @Override
    public boolean validate(List<String> warnings) {
        return properties.getProperty("className") != null;
    }

    @Override
    public void initialized(IntrospectedTable table) {
        table.setExampleType(context.getJavaModelGeneratorConfiguration().getTargetPackage()
                + "." + properties.getProperty("className") + "Example");
    }

    @Override
    public boolean modelExampleClassGenerated(TopLevelClass example, IntrospectedTable table) {
        example.getFields().removeIf(field -> "distinct".equals(field.getName()));
        example.getMethods().removeIf(method -> "setDistinct".equals(method.getName())
                || "isDistinct".equals(method.getName()));
        example.getMethods().stream().filter(method -> "clear".equals(method.getName()))
                .forEach(method -> method.getBodyLines().removeIf("distinct = false;"::equals));
        return true;
    }

    @Override
    public boolean sqlMapSelectByExampleWithoutBLOBsElementGenerated(XmlElement element,
                                                                     IntrospectedTable table) {
        removeDistinct(element);
        return true;
    }

    @Override
    public boolean sqlMapSelectByExampleWithBLOBsElementGenerated(XmlElement element,
                                                                  IntrospectedTable table) {
        removeDistinct(element);
        return true;
    }

    @Override
    public boolean clientSelectByPrimaryKeyMethodGenerated(Method method, Interface mapper,
                                                           IntrospectedTable table) {
        var lockingRead = new Method(method);
        lockingRead.setName("selectByPrimaryKeyForUpdate");
        mapper.addMethod(lockingRead);
        return true;
    }

    @Override
    public boolean sqlMapDocumentGenerated(Document document, IntrospectedTable table) {
        var root = document.getRootElement();
        var primaryRead = root.getElements().stream()
                .filter(XmlElement.class::isInstance)
                .map(XmlElement.class::cast)
                .filter(element -> element.getAttributes().stream().anyMatch(attribute ->
                        "id".equals(attribute.getName())
                                && "selectByPrimaryKey".equals(attribute.getValue())))
                .findFirst();
        primaryRead.ifPresent(element -> {
            var lockingRead = new XmlElement(element);
            lockingRead.getAttributes().removeIf(attribute -> "id".equals(attribute.getName()));
            lockingRead.addAttribute(new Attribute("id", "selectByPrimaryKeyForUpdate"));
            // Every invocation must reach the current connection, including reentrant locks.
            lockingRead.addAttribute(new Attribute("flushCache", "true"));
            lockingRead.addAttribute(new Attribute("useCache", "false"));
            lockingRead.addElement(new TextElement("for update"));
            root.addElement(lockingRead);
        });
        return true;
    }

    private static void removeDistinct(XmlElement element) {
        element.getElements().removeIf(child -> child instanceof XmlElement conditional
                && "if".equals(conditional.getName())
                && conditional.getAttributes().stream().anyMatch(attribute ->
                "test".equals(attribute.getName()) && "distinct".equals(attribute.getValue())));
    }
}
