package dev.mesbase.infrastructure.persistence;

/** Identity hooks only; propagation and JDBC completion remain Spring responsibilities. */
interface TransactionRouting {
    void begin();
    void verify();
    Object suspend();
    void resume(Object state);
    void end();
}
