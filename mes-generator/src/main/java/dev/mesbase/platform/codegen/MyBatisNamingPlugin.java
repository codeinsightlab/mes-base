package dev.mesbase.platform.codegen;

import java.util.List;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.PluginAdapter;

/**
 * Naming adapter only; does not inject business fields, SQL policies or runtime registrations.
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
}
