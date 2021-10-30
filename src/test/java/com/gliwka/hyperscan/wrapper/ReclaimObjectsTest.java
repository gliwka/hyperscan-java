package com.gliwka.hyperscan.wrapper;

import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Disabled("Uses static counters and can't run after other tests.")
public class ReclaimObjectsTest {
//    static {
//        System.setProperty("org.bytedeco.javacpp.logger.debug", "true");
//    }

    @Test
    public void test() throws CompileErrorException, InterruptedException {
        Database db = Database.compile(new Expression("Te?st"));
        // some reclaimable objects are left behind after compile
        assertEquals(4, Pointer.totalCount());

        try (Scanner scanner = new Scanner()) {
            scanner.allocScratch(db);
            assertEquals(6, Pointer.totalCount());
            scanner.scan(db, "Test");
            assertEquals(6, Pointer.totalCount());
        }
        assertEquals(4, Pointer.totalCount());

        WeakReference<Database> ref = new WeakReference<>(db);
        //noinspection UnusedAssignment
        db = null;

        int c = 0;
        while (ref.get() != null && c++ < 5) {
            Thread.sleep(200);
            System.gc();
        }

        assertNull(ref.get(), "Database not reclaimed.");
        Thread.sleep(100);
        assertEquals(0, Pointer.totalCount(), "Not all objects reclaimed");
        assertEquals(0, Pointer.totalBytes());
    }
}
