package com.vm2st.patternhunter;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Latest request wins. One running analysis, at most one queued request, no stale UI delivery. */
final class AnalysisDispatcher implements AutoCloseable {
    interface Analyzer { AnalysisSummary analyze(String text); }
    interface Listener {
        void onResult(AnalysisSummary result);
        void onError(RuntimeException error);
    }
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), runnable -> new Thread(runnable, "PatternHunter-analysis"));
    private final Executor main;
    private final Analyzer analyzer;
    private Future<?> latest;
    private long generation;
    private boolean closed;

    AnalysisDispatcher(Executor main, Analyzer analyzer) { this.main = main; this.analyzer = analyzer; }

    synchronized void cancel() {
        generation++;
        if (latest != null) latest.cancel(true);
        latest = null;
        worker.getQueue().clear();
    }

    synchronized void submit(String text, Listener listener) {
        if (closed) return;
        cancel();
        final long version = generation;
        try {
            latest = worker.submit(() -> {
                AnalysisSummary result;
                try { result = analyzer.analyze(text); }
                catch (CancellationException cancelled) { return; }
                catch (RuntimeException error) { deliver(version, () -> listener.onError(error)); return; }
                deliver(version, () -> listener.onResult(result));
            });
        } catch (RejectedExecutionException ignored) { /* A closed screen must not receive a result. */ }
    }

    private synchronized void deliver(long version, Runnable callback) {
        if (closed || version != generation) return;
        main.execute(() -> {
            synchronized (AnalysisDispatcher.this) {
                if (!closed && version == generation) callback.run();
            }
        });
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        cancel();
        worker.shutdownNow();
    }
}
