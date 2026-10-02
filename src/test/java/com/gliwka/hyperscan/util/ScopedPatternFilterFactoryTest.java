package com.gliwka.hyperscan.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests for {@link ScopedPatternFilterFactory}, covering its core responsibilities: per-thread
 * caching, thread isolation, lifecycle/closure semantics, automatic cleanup of dead threads'
 * resources, the no-filterable fast path, and input validation.
 */
class ScopedPatternFilterFactoryTest {

    // A simple, Hyperscan-compatible pattern.
    private final List<Pattern> testPatterns = Collections.singletonList(Pattern.compile("test"));

    // === Per-thread caching ===
    @Test
    void get_shouldReuseTheSameDelegateForTheSameThread() {
        try (ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(testPatterns)) {
            ScopedPatternFilter<Pattern> proxy1 = factory.get();
            ScopedPatternFilter<Pattern> proxy2 = factory.get();

            assertThat(proxy1).isInstanceOf(ScopedPatternFilterProxy.class);
            assertThat(proxy2).isInstanceOf(ScopedPatternFilterProxy.class);
            // The underlying thread-local instance must be reused.
            assertThat(getDelegate(proxy1)).isSameAs(getDelegate(proxy2));
            assertThat(factory.getRefKeeper()).hasSize(1);
        }
    }

    // === Thread isolation ===
    @Test
    void get_shouldCreateDistinctDelegatesForDifferentThreads() throws ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(testPatterns)) {
            Future<ScopedPatternFilter<Pattern>> future1 = executor.submit(() -> getDelegate(factory.get()));
            Future<ScopedPatternFilter<Pattern>> future2 = executor.submit(() -> getDelegate(factory.get()));

            ScopedPatternFilter<Pattern> delegate1 = future1.get();
            ScopedPatternFilter<Pattern> delegate2 = future2.get();

            assertThat(delegate1).isNotNull();
            assertThat(delegate2).isNotNull();
            assertThat(delegate1).isNotSameAs(delegate2);
            assertThat(factory.getRefKeeper()).hasSize(2);
        } finally {
            executor.shutdown();
        }
    }

    // === Filtering through the factory ===
    @Test
    void get_filterShouldReturnMatchedAndIncompatibleCandidates() {
        Pattern compatible = Pattern.compile("foobar");
        Pattern incompatible = Pattern.compile("\\R"); // \R (linebreak) is not supported by Hyperscan
        List<Pattern> patterns = Arrays.asList(compatible, incompatible);

        try (ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(patterns)) {
            ScopedPatternFilter<Pattern> filter = factory.get();

            assertThat(filter.filter("see foobar here")).containsExactlyInAnyOrder(compatible, incompatible);
            assertThat(filter.filter("nothing here")).containsExactly(incompatible);
        }
    }

    // === Closure invalidates dispensed filters ===
    @Test
    void close_shouldInvalidatePreviouslyDispensedFilters() {
        ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(testPatterns);
        ScopedPatternFilter<Pattern> filter = factory.get();
        assertThat(filter.filter("test")).isNotEmpty();

        factory.close();

        assertThatThrownBy(() -> filter.filter("test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This pattern filter has already been closed.");
    }

    // === Closure prevents new filters ===
    @Test
    void get_shouldThrowAfterFactoryIsClosed() {
        ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(testPatterns);
        factory.close();

        assertThatThrownBy(factory::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This ScopedPatternFilterFactory has already been closed.");
    }

    // === Closure is idempotent ===
    @Test
    void close_shouldBeIdempotent() {
        ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(testPatterns);
        factory.get();

        factory.close();
        assertThatCode(factory::close).doesNotThrowAnyException();
    }

    // === Factory instance isolation ===
    @Test
    void close_shouldNotAffectOtherFactoryInstances() {
        List<Pattern> otherPatterns = Collections.singletonList(Pattern.compile("other"));
        try (ScopedPatternFilterFactory<Pattern> factory1 = ScopedPatternFilterFactory.ofPatterns(testPatterns);
             ScopedPatternFilterFactory<Pattern> factory2 = ScopedPatternFilterFactory.ofPatterns(otherPatterns)) {

            ScopedPatternFilter<Pattern> filter1 = factory1.get();
            ScopedPatternFilter<Pattern> filter2 = factory2.get();

            factory1.close();

            assertThatThrownBy(() -> filter1.filter("test")).isInstanceOf(IllegalStateException.class);
            // The second factory remains fully operational.
            assertThat(factory2.get()).isNotNull();
            assertThat(filter2.filter("other")).isNotEmpty();
        }
    }

    // === Proxy close is a no-op ===
    @Test
    void get_shouldReturnProxyWhoseCloseIsANoOp() throws Exception {
        try (ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(testPatterns)) {
            ScopedPatternFilter<Pattern> proxy = factory.get();
            ScopedPatternFilter<Pattern> delegate = getDelegate(proxy);

            proxy.close();

            // The underlying delegate must remain usable.
            assertThat(delegate.filter("test")).isNotEmpty();
        }
    }

    // === No-filterable fast path ===
    @Test
    void get_whenNoPatternIsFilterable_shouldReturnPatternOnlyFilter() {
        // Only Hyperscan-incompatible patterns (\R, \b{3}) => no database is compiled.
        List<Pattern> incompatible = Arrays.asList(Pattern.compile("\\R"), Pattern.compile("a\\b{3}"));
        try (ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(incompatible)) {
            ScopedPatternFilter<Pattern> filter = factory.get();

            assertThat(filter).isInstanceOf(PatternOnlyFilter.class);
            assertThat(filter.filter("anything")).containsExactlyInAnyOrderElementsOf(incompatible);
            // No filter implementations were created, so nothing is tracked for cleanup.
            assertThat(factory.getRefKeeper()).isEmpty();
        }
    }

    // === Automatic cleanup when a thread dies ===
    @Test
    void get_shouldAutomaticallyReclaimResourcesWhenItsThreadDies() throws InterruptedException {
        try (ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(testPatterns)) {
            // Create a filter on a short-lived thread, then let the thread terminate.
            Thread ephemeral = new Thread(factory::get);
            ephemeral.start();
            ephemeral.join();

            assertThat(factory.getRefKeeper()).hasSize(1);

            // Make the Thread (and thus its thread-local filter) collectable.
            //noinspection UnusedAssignment
            ephemeral = null;

            long deadline = System.currentTimeMillis() + 5000;
            boolean cleaned = false;
            while (System.currentTimeMillis() < deadline) {
                System.gc();
                if (factory.getRefKeeper().isEmpty()) {
                    cleaned = true;
                    break;
                }
                Thread.sleep(200);
            }

            if (!cleaned) {
                fail("Automatic cleanup did not drain refKeeper within the timeout.");
            }
        }
    }

    // === Concurrency: get() racing close() must never use a freed database ===
    @Test
    void get_concurrentWithClose_neverCrashesAndEventuallyRejects() throws InterruptedException {
        // Repeatedly race fresh threads calling get() against close() on the same factory. A
        // use-after-free of the shared database would crash the JVM; otherwise every call must
        // either return a usable filter or fail cleanly with IllegalStateException.
        for (int round = 0; round < 50; round++) {
            ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(testPatterns);
            int racers = 8;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(racers);
            AtomicReference<Throwable> unexpected = new AtomicReference<>();

            for (int i = 0; i < racers; i++) {
                new Thread(() -> {
                    try {
                        start.await();
                        ScopedPatternFilter<Pattern> filter = factory.get();
                        filter.filter("test");
                    } catch (IllegalStateException expected) {
                        // Acceptable: the factory was closed before/while we created our filter.
                    } catch (Throwable t) {
                        unexpected.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            start.countDown();
            factory.close();
            done.await();

            assertThat(unexpected.get()).isNull();
        }
    }

    // === Input validation ===
    @Test
    void ofPatterns_shouldRejectNullIterable() {
        assertThatThrownBy(() -> ScopedPatternFilterFactory.ofPatterns(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("The patterns iterable must not be null.");
    }

    @Test
    void ofPatterns_shouldRejectEmptyIterable() {
        assertThatThrownBy(() -> ScopedPatternFilterFactory.ofPatterns(Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one pattern must be provided; the patterns iterable was empty.");
    }

    @Test
    void create_shouldRejectNullPatternMapper() {
        assertThatThrownBy(() -> ScopedPatternFilterFactory.create(testPatterns, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("The patternMapper function must not be null.");
    }

    @Test
    void create_shouldRejectMapperReturningNull() {
        List<String> sources = Collections.singletonList("ignored");
        Function<String, Pattern> nullMapper = s -> null;

        assertThatThrownBy(() -> ScopedPatternFilterFactory.create(sources, nullMapper))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("The patternMapper returned null for pattern: ignored.");
    }

    @Test
    void create_shouldSupportArbitrarySourceTypesViaMapper() {
        List<String> sources = Arrays.asList("foobar", "widget");
        try (ScopedPatternFilterFactory<String> factory = ScopedPatternFilterFactory.create(sources, Pattern::compile)) {
            assertThat(factory.get().filter("see foobar here")).containsExactly("foobar");
        }
    }

    /** Extracts the wrapped delegate from a {@link ScopedPatternFilterProxy} via reflection. */
    @SuppressWarnings("unchecked")
    private static ScopedPatternFilter<Pattern> getDelegate(ScopedPatternFilter<Pattern> proxy) {
        if (!(proxy instanceof ScopedPatternFilterProxy)) {
            throw new IllegalArgumentException("Expected a proxy, but got " + proxy.getClass().getName());
        }
        try {
            Field delegateField = ScopedPatternFilterProxy.class.getDeclaredField("delegate");
            delegateField.setAccessible(true);
            return (ScopedPatternFilter<Pattern>) delegateField.get(proxy);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to read proxy delegate via reflection", e);
        }
    }
}
