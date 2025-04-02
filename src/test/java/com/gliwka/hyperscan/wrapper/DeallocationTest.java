package com.gliwka.hyperscan.wrapper;

import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;

import static org.junit.jupiter.api.Assertions.*;


class DeallocationTest {
    @Test
    public void ensureAllNativeMemoryIsBeingFreed() throws CompileErrorException, InterruptedException {
        long baseline = Pointer.totalCount();

        try(Database db = Database.compile(new Expression("Te?st"))) {
            // we only expect the database pointer, all compile artifacts should have been freed
            assertEquals(baseline + 1, Pointer.totalCount());

            try (Scanner scanner = new Scanner()) {
                scanner.allocScratch(db);

                // now there should be two pointers, one for the db and one for the scratch space
                assertEquals(baseline + 2, Pointer.totalCount());
                scanner.scan(db, "Test");

                // same after scanning, it should stay at two - all artifacts from matching should be gone
                assertEquals(baseline + 2, Pointer.totalCount());
            }

            assertEquals(baseline + 1, Pointer.totalCount());
        }

        // All resources are closed, there should be no more open native references
        assertEquals(baseline, Pointer.totalCount());
    }

    @Test
    void nativeHandlesShouldBeGarbageCollectable() throws CompileErrorException {
        long baseline = Pointer.totalCount();

        Database db = Database.compile(new Expression("Te?st"));
        Scanner scanner = new Scanner();
        scanner.allocScratch(db);

        WeakReference<Database> dbRef = new WeakReference<>(db);
        WeakReference<Scanner> scannerRef = new WeakReference<>(scanner);
        db = null;
        scanner = null;

        assertEquals(baseline + 2, Pointer.totalCount());
        assertNotNull(dbRef);
        assertNotNull(scannerRef);

        for(int i = 0; i < 100; i++) {
            System.gc();
        }

        assertNull(dbRef.get());
        assertNull(scannerRef.get());
        assertTrue(Pointer.totalCount() <= baseline);
    }

    @Test
    void databaseShouldThrowExceptionOnCallingSizeAfterClose() {
        try {
            Database db = Database.compile(new Expression("test"));
            db.close();
            db.getSize();
            fail("We should not be able to call getSize on a deallocated database");
        }
        catch(IllegalStateException e) {
            //expected
        }
        catch(Exception t) {
            fail(t);
        }
    }

    @Test
    void scrannerShouldThrowExceptionOnCallingSizeAfterClose() {
        try {
            Database db = Database.compile(new Expression("test"));
            Scanner scanner = new Scanner();
            scanner.allocScratch(db);
            scanner.close();
            scanner.getSize();
            fail("We should not be able to call getSize on a deallocated scanner scratch space");
        }
        catch(IllegalStateException e) {
            //expected
        }
        catch(Exception t) {
            fail(t);
        }
    }
}
