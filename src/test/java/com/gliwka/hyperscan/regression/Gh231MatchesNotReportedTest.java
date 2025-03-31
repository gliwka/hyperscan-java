package com.gliwka.hyperscan.regression;

import com.gliwka.hyperscan.wrapper.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// https://github.com/gliwka/hyperscan-java/issues/231
public class Gh231MatchesNotReportedTest {
    @Test
    void shouldHaveAMatch() throws CompileErrorException {
        Scanner scanner = new Scanner();

        Database db = Database.compile(
                new Expression("^.*TAPIOCA.*EX.*$", ExpressionFlag.CASELESS, 10055730),
                new Expression("^.*BOBA.*GUY.*$", ExpressionFlag.CASELESS, 10055725),
                new Expression("^.*PAYPAL.*JACQUES.*MARI.*$", ExpressionFlag.CASELESS, 10055723),
                new Expression("^GUCCI FRANCE.*$", ExpressionFlag.CASELESS, 10055736),
                new Expression("^PAYPAL \\*YVESSAINTL.*$", ExpressionFlag.CASELESS, 10055734),
                new Expression("^DD.*FIREHO 855-973-1040$", ExpressionFlag.CASELESS, 10055728),
                new Expression("^.*XING.*FU.*TAN.*$", ExpressionFlag.CASELESS, 10055733)
        );

        scanner.allocScratch(db);
        List<Match> matches = scanner.scan(db, "TAPIOCA HOUSE TUXTLA GUTIER MEX");
        assertFalse(matches.isEmpty());
    }
}
