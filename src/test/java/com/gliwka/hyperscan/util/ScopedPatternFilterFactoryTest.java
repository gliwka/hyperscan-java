package com.gliwka.hyperscan.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * Expert-level tests for ScopedPatternFilterFactory using JUnit 5 and Java 8.
 * These tests focus on the core responsibilities: lifecycle, thread-safety,
 * automatic resource reclamation, and instance isolation.
 */
class ScopedPatternFilterFactoryTest {

    // A simple, Hyperscan-compatible pattern for reliable factory initialization.
    private final List<Pattern> testPatterns = Collections.singletonList(Pattern.compile("test"));

    // === Test Case 1: Thread-Local Caching Behavior ===
    @Test
    void get_shouldReturnSameDelegateInstanceForSameThread() {
        try (ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(testPatterns)) {
            ScopedPatternFilter<Pattern> filter1 = factory.get();
            ScopedPatternFilter<Pattern> filter2 = factory.get();

            assertThat(filter1).isInstanceOf(ScopedPatternFilterProxy.class);
            assertThat(filter2).isInstanceOf(ScopedPatternFilterProxy.class);

            // Crucially, the underlying delegate instance must be the same.
            assertThat(getDelegate(filter1)).isSameAs(getDelegate(filter2));
            assertThat(factory.getThreadLocalFilters().size()).isEqualTo(1);
        }
    }

    // === Test Case 2: Thread Isolation ===
    @Test
    void get_shouldReturnDifferentDelegateInstancesForDifferentThreads() throws ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(testPatterns)) {
            Future<ScopedPatternFilter<Pattern>> future1 = executor.submit(() -> getDelegate(factory.get()));
            Future<ScopedPatternFilter<Pattern>> future2 = executor.submit(() -> getDelegate(factory.get()));

            ScopedPatternFilter<Pattern> delegate1 = future1.get();
            ScopedPatternFilter<Pattern> delegate2 = future2.get();

            assertThat(delegate1).isNotNull();
            assertThat(delegate2).isNotNull();
            assertThat(delegate1).isNotSameAs(delegate2);
            assertThat(factory.getThreadLocalFilters().size()).isEqualTo(2);
        } finally {
            executor.shutdown();
        }
    }

    // === Test Case 3: Explicit Close and Resource Invalidation ===
    @Test
    void close_shouldInvalidateAllActiveFiltersCreatedByIt() {
        try (ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(testPatterns)) {
            // Dispense a filter and confirm it works.
            ScopedPatternFilter<Pattern> activeFilter = factory.get();
            assertThat(activeFilter.filter("test")).isNotEmpty();

            // Close the factory.
            factory.close();

            // The previously dispensed filter must now be unusable and throw an exception.
            assertThatThrownBy(() -> activeFilter.filter("test")).isInstanceOf(IllegalStateException.class).hasMessage("Pattern filter is closed");
        }
    }

    // === Test Case 4: State after Closing ===
    @Test
    void close_shouldPreventNewFiltersFromBeingCreated() {
        ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(testPatterns);
        factory.close();

        assertThatThrownBy(factory::get).isInstanceOf(IllegalStateException.class).hasMessage("ScopedPatternFilterFactory is closed.");
    }

    // === Test Case 5: Automatic Resource Reclamation via GC ===
    @Test
    void get_filterShouldBeCleanedUpAutomaticallyWhenItsThreadDies() throws InterruptedException {
        try (ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(testPatterns)) {
            // Step 1: Create a filter in a new thread, which then terminates.
            Thread ephemeralThread = new Thread(factory::get);
            ephemeralThread.start();
            ephemeralThread.join(); // Wait for the thread to die.

            // At this point, the factory is tracking the created filter and its cleaner.
            assertThat(factory.getRefKeeper().size()).isEqualTo(1);
            assertThat(factory.getThreadLocalFilters().size()).isEqualTo(1);

            // Step 2: Make the Thread object unreachable to allow it to be GC'd.
            // noinspection UnusedAssignment
            ephemeralThread = null;
            factory.getThreadLocalFilters().clear();

            // Step 3: Repeatedly suggest GC and wait for the factory's background cleaner task
            // to process the phantom reference and remove it from the tracking set.
            long timeout = System.currentTimeMillis() + 5000; // 5-second timeout
            boolean wasCleaned = false;
            while (System.currentTimeMillis() < timeout) {
                System.gc();
                if (factory.getRefKeeper().isEmpty()) {
                    wasCleaned = true;
                    break;
                }
                Thread.sleep(200);
            }

            if (!wasCleaned) {
                fail("Automatic resource cleanup failed: refKeeper was not cleared within the timeout.");
            }
        }
    }

    // === Test Case 6: Factory Instance Isolation ===
    @Test
    void close_shouldHaveNoEffectOnOtherFactoryInstances() {
        List<Pattern> patterns2 = Collections.singletonList(Pattern.compile("other"));

        try (ScopedPatternFilterFactory<Pattern> factory1 = ScopedPatternFilterFactory.ofPatterns(testPatterns); ScopedPatternFilterFactory<Pattern> factory2 = ScopedPatternFilterFactory.ofPatterns(patterns2)) {

            ScopedPatternFilter<Pattern> filter1 = factory1.get();
            ScopedPatternFilter<Pattern> filter2 = factory2.get();

            // Close the first factory.
            factory1.close();

            // Verify the second factory and its filter remain fully operational.
            assertThat(factory2.get()).isNotNull();
            assertThat(filter2.filter("other")).isNotEmpty();

            // Verify the first factory's filter is now dead.
            assertThatThrownBy(() -> filter1.filter("test")).isInstanceOf(IllegalStateException.class);
        }
    }

    // === Test Case 7: Proxy Behavior Verification ===
    @Test
    void get_returnsProxyWhoseCloseMethodIsANoOp() throws IOException {
        try (ScopedPatternFilterFactory<Pattern> factory = ScopedPatternFilterFactory.ofPatterns(testPatterns)) {
            ScopedPatternFilter<Pattern> proxy = factory.get();
            ScopedPatternFilter<Pattern> delegate = getDelegate(proxy);

            // Calling close on the proxy should do nothing.
            proxy.close();

            // The underlying delegate should remain active and usable.
            assertThat(delegate.filter("test")).isNotEmpty();
        }
    }

    // === Test Case 8: Constructor Input Validation ===
    @Test
    void constructor_shouldRejectInvalidInputs() {
        // Null patterns iterable
        assertThatThrownBy(() -> ScopedPatternFilterFactory.ofPatterns(null)).isInstanceOf(NullPointerException.class).hasMessage("patterns cannot be null");

        // Empty patterns iterable
        assertThatThrownBy(() -> ScopedPatternFilterFactory.ofPatterns(Collections.emptyList())).isInstanceOf(IllegalArgumentException.class).hasMessage("patterns cannot be empty");

        // Null pattern mapper
        assertThatThrownBy(() -> new ScopedPatternFilterFactory<>(testPatterns, null)).isInstanceOf(NullPointerException.class).hasMessage("patternMapper cannot be null");
    }

    /**
     * Helper method to extract the underlying delegate from the proxy via reflection for testing.
     */
    @SuppressWarnings("unchecked")
    private ScopedPatternFilter<Pattern> getDelegate(ScopedPatternFilter<Pattern> proxy) {
        try {
            if (!(proxy instanceof ScopedPatternFilterProxy)) {
                throw new IllegalArgumentException("Expected a proxy instance, but got " + proxy.getClass().getName());
            }
            Field delegateField = ScopedPatternFilterProxy.class.getDeclaredField("delegate");
            delegateField.setAccessible(true);
            return (ScopedPatternFilter<Pattern>) delegateField.get(proxy);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to get delegate via reflection", e);
        }
    }
}