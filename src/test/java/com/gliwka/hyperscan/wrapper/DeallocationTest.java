package com.gliwka.hyperscan.wrapper;

import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.lang.ref.WeakReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author gliwka
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeallocationTest {
    @Test
    @Order(0)
    public void ensureAllNativeMemoryIsBeingFreed() throws CompileErrorException, IOException {
        try(Database db = Database.compile(new Expression("Te?st"))) {
            // we only expect the database pointer, all compile artifacts should have been freed
            assertEquals(1, Pointer.totalCount());

            try (Scanner scanner = new Scanner()) {
                scanner.allocScratch(db);

                // now there should be two pointers, one for the db and one for the callback
                assertEquals(2, Pointer.totalCount());

                // creating an additional scanner should not increase it
                Scanner additionalScanner = new Scanner();
                additionalScanner.allocScratch(db);
                assertEquals(2, Pointer.totalCount());
                // Close the additional scanner to prevent resource leak
                additionalScanner.close();

                scanner.scan(db, "Test");
                // same after scanning, it should stay at two - all artifacts from matching should be gone
                assertEquals(2, Pointer.totalCount());
            }

            assertEquals(2, Pointer.totalCount());
        }

        // All resources are closed, there should be only the scanner callback remaining
        assertEquals(1, Pointer.totalCount());
    }

    @Test
    @Order(1)
    void nativeHandlesShouldBeGarbageCollectable() throws CompileErrorException, IOException {
        // expect baseline to be 1 - only the open callback from the previous test should exist
        assertEquals(1, Pointer.totalCount());

        Database db = Database.compile(new Expression("Te?st"));
        Scanner scanner = new Scanner();
        scanner.allocScratch(db);
        scanner.close();

        WeakReference<Database> dbRef = new WeakReference<>(db);
        WeakReference<Scanner> scannerRef = new WeakReference<>(scanner);
        db = null;
        scanner = null;

        assertEquals(2, Pointer.totalCount());
        assertNotNull(dbRef);
        assertNotNull(scannerRef);

        for(int i = 0; i < 100; i++) {
            System.gc();
        }

        // all should be freed, besides the scanner callback
        assertNull(dbRef.get());
        assertNull(scannerRef.get());
        assertEquals(1, Pointer.totalCount());
    }

    @Test
    @Order(2)
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
