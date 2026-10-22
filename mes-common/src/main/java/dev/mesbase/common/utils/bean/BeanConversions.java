package dev.mesbase.common.utils.bean;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import dev.mesbase.common.core.page.PageData;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import tools.jackson.databind.node.ObjectNode;

/**
 * Target-type property conversion, including immutable records and typed nested lists.
 * The target response type is the field whitelist. Never use this to bind commands or persistence writes.
 */
public final class BeanConversions {
    private static final JsonMapper MAPPER = mapper();

    private BeanConversions() { }

    private static JsonMapper mapper() {
        var numbers = new SimpleModule();
        numbers.addSerializer(BigDecimal.class, new ValueSerializer<>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator output, SerializationContext serializers) {
                output.writeString(value.toPlainString());
            }
        });
        return JsonMapper.builder().findAndAddModules().addModule(numbers)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    }

    public static <T> T convert(Object source, Class<T> target) {
        if (source == null) return null;
        return MAPPER.convertValue(source, target);
    }

    /** Only typed, trusted Controller-derived attributes may supplement an existing response projection. */
    public static <T> T convertWith(Object source, Object attributes, Class<T> target) {
        if (source == null) return null;
        if (!(MAPPER.valueToTree(source) instanceof ObjectNode properties)
                || !(MAPPER.valueToTree(attributes) instanceof ObjectNode extra))
            throw new IllegalArgumentException("Response projection requires object properties");
        properties.setAll(extra);
        return MAPPER.convertValue(properties, target);
    }

    @FunctionalInterface
    public interface PageFactory<T, R> {
        R create(List<T> items, long total, int offset, int limit);
    }

    /** Custom business-derived rows use this path; pagination metadata is still assembled only here. */
    public static <S, T, R> R convertPage(PageData<S> source, Function<? super S, ? extends T> rows,
                                         PageFactory<T, R> result) {
        return result.create(source.items().stream().<T>map(rows).toList(), source.total(), source.offset(), source.limit());
    }

    /** Maps rows using the concrete target's generic item type and preserves all pagination metadata. */
    public static <S, T> T convertPage(PageData<S> source, Class<T> target) {
        return convert(source, target);
    }
}
