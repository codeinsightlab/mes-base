package dev.mesbase.platform.codegen;

import java.util.List;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.PluginAdapter;
import org.mybatis.generator.api.dom.java.TopLevelClass;
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

    private static void removeDistinct(XmlElement element) {
        element.getElements().removeIf(child -> child instanceof XmlElement conditional
                && "if".equals(conditional.getName())
                && conditional.getAttributes().stream().anyMatch(attribute ->
                "test".equals(attribute.getName()) && "distinct".equals(attribute.getValue())));
    }
}
