package dev.mesbase.integration.erp;
/** Minimum adapter boundary. No vendor or business operation is invented in phase one. */
public interface ErpAdapter { ErpProvider provider(); void verifyConnection(ErpConnectionBinding binding); }
