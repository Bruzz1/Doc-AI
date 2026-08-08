package com.bruce.docai.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Central place for document-pipeline observability. Exposes counters and timers
 * through Micrometer so they surface on the Actuator {@code /metrics} and
 * {@code /prometheus} endpoints.
 */
@Component
public class DocumentMetrics {

    private final MeterRegistry registry;
    private final Counter uploadsAccepted;
    private final Counter ingestSucceeded;
    private final Counter ingestFailed;
    private final Timer ingestTimer;

    public DocumentMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.uploadsAccepted = Counter.builder("docai.document.uploads.accepted")
                .description("Documents accepted for asynchronous ingestion")
                .register(registry);
        this.ingestSucceeded = Counter.builder("docai.document.ingest.completed")
                .description("Document ingestions that completed")
                .tag("outcome", "success")
                .register(registry);
        this.ingestFailed = Counter.builder("docai.document.ingest.completed")
                .description("Document ingestions that completed")
                .tag("outcome", "failure")
                .register(registry);
        this.ingestTimer = Timer.builder("docai.document.ingest.duration")
                .description("Wall-clock time to parse, embed and store a document")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void recordUploadAccepted() {
        uploadsAccepted.increment();
    }

    public void recordIngestSuccess(Duration duration, int chunkCount) {
        ingestSucceeded.increment();
        ingestTimer.record(duration);
        registry.summary("docai.document.chunks").record(chunkCount);
    }

    public void recordIngestFailure(Duration duration) {
        ingestFailed.increment();
        ingestTimer.record(duration);
    }
}
