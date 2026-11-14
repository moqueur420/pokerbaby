package io.pokerbaby.benchmark;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.LongSupplier;

/** Common warmup and median reporting for standalone, single-thread measurements. */
public final class BenchmarkSupport {
    private static final long WARMUP_NANOS = 2_000_000_000L;
    private static final long MEASUREMENT_NANOS = 1_000_000_000L;
    private static volatile long sink;

    private BenchmarkSupport() {
    }

    /** Prints the runtime context without starting additional application threads. */
    public static void environment() {
        System.out.println("Java: " + System.getProperty("java.runtime.version"));
        System.out.println("Vendor: " + System.getProperty("java.vendor"));
        System.out.println("VM: " + System.getProperty("java.vm.name"));
        System.out.println("OS: " + System.getProperty("os.name") + " / " + System.getProperty("os.arch"));
        System.out.println("Processor: " + System.getenv("PROCESSOR_IDENTIFIER"));
        System.out.println("Active processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Each case: >=2 s warmup (>=2 batches), then 3 passes of >=1 s; median throughput");
    }

    /**
     * Warms and measures a reproducible batch; callbacks run once per batch, not per operation.
     * @param name measurement label
     * @param operations number of operations in each batch
     * @param batch work returning a consumed checksum
     */
    public static void measure(String name, int operations, LongSupplier batch) {
        System.out.println("Case: " + name + "; operations/batch=" + operations);
        long warmupStart = System.nanoTime();
        int warmupBatches = 0;
        do {
            sink = batch.getAsLong();
            warmupBatches++;
        } while (warmupBatches < 2 || System.nanoTime() - warmupStart < WARMUP_NANOS);
        double[] rates = new double[3];
        for (int pass = 0; pass < rates.length; pass++) {
            long start = System.nanoTime();
            long completed = 0;
            long nanos;
            do {
                sink = batch.getAsLong();
                completed += operations;
                nanos = System.nanoTime() - start;
            } while (nanos < MEASUREMENT_NANOS);
            rates[pass] = completed * 1_000_000_000.0 / nanos;
            System.out.printf(Locale.ROOT, "  Pass %d: %d operations, %.3f s, %.0f operations/s; checksum=%d%n",
                    pass + 1, completed, nanos / 1_000_000_000.0, rates[pass], sink);
        }
        Arrays.sort(rates);
        System.out.printf(Locale.ROOT, "Median %s: %.0f operations/s; spread=%.1f%%%n",
                name, rates[1], (rates[2] - rates[0]) * 100.0 / rates[1]);
    }
}
