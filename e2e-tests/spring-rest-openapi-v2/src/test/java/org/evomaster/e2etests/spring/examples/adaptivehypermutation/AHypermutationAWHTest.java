package org.evomaster.e2etests.spring.examples.adaptivehypermutation;

import com.foo.rest.examples.spring.adaptivehypermutation.AHypermutationRestController;
import org.evomaster.core.problem.rest.HttpVerb;
import org.evomaster.core.problem.rest.RestIndividual;
import org.evomaster.core.search.Solution;
import org.evomaster.e2etests.spring.examples.SpringTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * with same budget, i.e., 20_000 HTTP calls (see {@link #budget})
 * for the example (only include /foos/{x} endpoints see {@link #initClass()}),
 * we tested the performances of default MIO and MIO-WH* based on
 * a capability of covering expected targets(see {@link #countExpectedCoveredTargets(Solution, List)}).
 * There are three prerequisites of the expected targets, i.e.,
 *      1) there must exist 3 foo in DB and 2) specified x must not exist in DB and 3) specified y is "foo"
 * NOTE THAT all of the prerequisites must be satisfied in order to reach the expected targets.
 *
 * Based on the tests, with 3 attempts,
 * 1) default MIO always covers less than 2 out of the 6 expected targets, i.e., 0 (likely) or 1;
 * 2) MIO-WH* is able to cover all of the 6 expected targets.
 *    In addition, we also provide a sample test generated by MIO-WH* {@link org.evomaster.e2etests.spring.examples.adaptivehypermutation.EvoMasterAWHSampleTest}
 *
 * Outputs by the tests can be found:
 * 1) target/em-tests/AWH/TestBase org.adaptivehypermuation.BaseTest is the test generated by default MIO
 * 2) target/em-tests/AWH/TestAHW org.adaptivehypermuation.AWHTest is the test generated by MIO-WH*
 * 3) target/em-tests/AWH/statistics.csv records detailed performance in coverage including target, line and branch.
 * 4) target/em-tests/AWH/snapshot.csv records more detailed performance i.e., every 5% of the used budget, throughout search,
 */
public class AHypermutationAWHTest extends AHypermuationTestBase {
    private static int budget = 20_000;
    private static String statisticsFile = TESTS_OUTPUT_ROOT_FOLDER + "/AWH/statistics.csv";
    private static String snapshotFile = TESTS_OUTPUT_ROOT_FOLDER + "/AWH/snapshot.csv";
    @Test
    public void testRunMIO() {
        
        List<String> msg = new ArrayList<>();
        assertThrows(Throwable.class, () -> {
            runTestHandlingFlakyAndCompilation(
                    "AWH/TestBase",
                    "org.adaptivehypermuation.BaseTest",
                    budget,
                    true,
                    (args) -> {

                        args.add("--probOfArchiveMutation");
                        args.add("0.0");

                        args.add("--weightBasedMutationRate");
                        args.add("false");

                        args.add("--adaptiveGeneSelectionMethod");
                        args.add("NONE");

                        args.add("--archiveGeneMutation");
                        args.add("NONE");

                        args.add("--enableTrackEvaluatedIndividual");
                        args.add("false");

                        args.add("--writeStatistics");
                        args.add("true");

                        args.add("--statisticsFile");
                        args.add(statisticsFile);

                        args.add("--appendToStatisticsFile");
                        args.add("true");

                        args.add("--snapshotInterval");
                        args.add("5");

                        args.add("--snapshotStatisticsFile");
                        args.add(snapshotFile);

                        args.add("--statisticsColumnId");
                        args.add("awh-example-base");

                        Solution<RestIndividual> solution = initAndRun(args);


                        int count = countExpectedCoveredTargets(solution, msg);

                        assertTrue(count >= 2);
                    }, 10);
        }, String.join("\n", msg));

    }

    @Test
    public void testRunMIOAWH() throws Throwable {

        runTestHandlingFlakyAndCompilation(
                "AWH/TestAHW",
                "org.adaptivehypermuation.AWHTest",
                budget,
                true,
                (args) -> {

                    args.add("--probOfArchiveMutation");
                    args.add("0.5");

                    args.add("--weightBasedMutationRate");
                    args.add("true");

                    args.add("--adaptiveGeneSelectionMethod");
                    args.add("APPROACH_IMPACT");

                    args.add("--archiveGeneMutation");
                    args.add("SPECIFIED_WITH_SPECIFIC_TARGETS");

                    args.add("--enableTrackEvaluatedIndividual");
                    args.add("true");

                    args.add("--writeStatistics");
                    args.add("true");

                    args.add("--statisticsFile");
                    args.add(statisticsFile);

                    args.add("--appendToStatisticsFile");
                    args.add("true");

                    args.add("--snapshotInterval");
                    args.add("5");

                    args.add("--snapshotStatisticsFile");
                    args.add(snapshotFile);

                    args.add("--statisticsColumnId");
                    args.add("awh-example-awh");

                    Solution<RestIndividual> solution = initAndRun(args);

                    List<String> msg = new ArrayList<>();
                    int count = countExpectedCoveredTargets(solution, msg);

                    assertEquals(6, count, String.join("\n", msg));
                }, 10);
    }

    private int countExpectedCoveredTargets(Solution<RestIndividual> solution , List<String> msg){
        int count = 0;
        count = countExpected(solution, HttpVerb.POST, 200, "/api/foos/{x}", "B0", count, msg);
        count = countExpected(solution, HttpVerb.POST, 200, "/api/foos/{x}", "B1", count, msg);
        count = countExpected(solution, HttpVerb.POST, 200, "/api/foos/{x}", "B2", count, msg);
        count = countExpected(solution, HttpVerb.POST, 200, "/api/foos/{x}", "B3", count, msg);
        count = countExpected(solution, HttpVerb.POST, 200, "/api/foos/{x}", "B4", count, msg);
        count = countExpected(solution, HttpVerb.POST, 200, "/api/foos/{x}", "B5", count, msg);
        return count;
    }

    @BeforeAll
    public static void initClass() throws Exception {
        SpringTestBase.initClass(new AHypermutationRestController(Arrays.asList("/api/bars/{a}")));
    }
}
