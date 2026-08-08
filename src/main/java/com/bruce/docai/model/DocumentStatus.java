package com.bruce.docai.model;

/**
 * Lifecycle of an uploaded document through the asynchronous ingestion pipeline.
 * PENDING and PROCESSING are transient states while embedding runs off the
 * request thread; INDEXED and FAILED are terminal.
 */
public enum DocumentStatus {
    PENDING,
    PROCESSING,
    INDEXED,
    FAILED
}
