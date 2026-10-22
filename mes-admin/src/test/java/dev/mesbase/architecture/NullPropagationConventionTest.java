package dev.mesbase.architecture;

import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces the user's source rule using Java syntax, including multiline and reversed conditions.
 */
class NullPropagationConventionTest {
    @Test
    void nullPropagationIsNotHiddenInConditionalExpressions() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("mes-common/pom.xml"))) root = root.getParent();
        List<Path> sources;
        try (var modules = Files.list(root)) {
            sources = new ArrayList<>();
            for (Path module : modules.filter(p -> p.getFileName().toString().startsWith("mes-")).toList()) {
                Path source = module.resolve("src");
                if (!Files.isDirectory(source)) continue;
                try (var files = Files.walk(source)) {
                    sources.addAll(files.filter(p -> p.toString().endsWith(".java")).toList());
                }
            }
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        List<String> violations = new ArrayList<>();
        try (var files = compiler.getStandardFileManager(null, null, null)) {
            var task = (JavacTask) compiler.getTask(null, files, diagnostic -> {
                    }, List.of("-proc:none"), null,
                    files.getJavaFileObjectsFromPaths(sources));
            for (var unit : task.parse()) {
                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitConditionalExpression(ConditionalExpressionTree expression, Void ignored) {
                        boolean nullableBranch = expression.getTrueExpression().getKind() == Tree.Kind.NULL_LITERAL
                                || expression.getFalseExpression().getKind() == Tree.Kind.NULL_LITERAL;
                        if (nullableBranch && expression.getCondition().toString().contains("null"))
                            violations.add(unit.getSourceFile().getName() + ": " + expression);
                        return super.visitConditionalExpression(expression, ignored);
                    }
                }.scan(unit, null);
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }
}
