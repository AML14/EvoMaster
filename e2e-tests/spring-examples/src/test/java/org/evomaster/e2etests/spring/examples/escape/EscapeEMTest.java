package org.evomaster.e2etests.spring.examples.escape;

import org.evomaster.core.problem.rest.HttpVerb;
import org.evomaster.core.problem.rest.RestIndividual;
import org.evomaster.core.search.Solution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EscapeEMTest extends EscapeTestBase {

    @Test
    public void testRunEM() throws Throwable {

        runTestHandlingFlakyAndCompilation(
                "EscapeEM",
                "org.bar.EscapeEM",
                10_000,
                (args) -> {

                    Solution<RestIndividual> solution = initAndRun(args);

                    assertTrue(solution.getIndividuals().size() >= 1);

                    assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/escape/containsDollar/{s}", "false");
                    assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/escape/containsQuote/{s}", "false");


                    assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/escape/containsDollar/{s}", "true");
                    assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/escape/containsQuote/{s}", "true");
                });
    }
}
