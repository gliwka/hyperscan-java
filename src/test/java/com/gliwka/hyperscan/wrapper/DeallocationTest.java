package com.gliwka.hyperscan.wrapper;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class DeallocationTest {
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
