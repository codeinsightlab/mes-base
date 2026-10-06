package dev.mesbase.infrastructure.persistence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.transaction.annotation.Transactional;

/** Selects one configured external connection before the standard Spring ERP transaction. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Transactional(transactionManager = "erpTransactionManager", rollbackFor = Exception.class)
public @interface ErpSource {
    String value();
}
