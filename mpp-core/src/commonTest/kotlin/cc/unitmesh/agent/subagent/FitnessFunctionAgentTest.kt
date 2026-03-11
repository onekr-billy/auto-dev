package cc.unitmesh.agent.subagent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for FitnessFunctionAgent data classes and schema.
 *
 * These tests verify that fitness functions can be correctly defined,
 * results parsed, and reports generated - ensuring LLM-generated code
 * can be evaluated against measurable acceptance criteria.
 */
class FitnessFunctionAgentTest {

    // ============= FitnessFunction Tests =============

    @Test
    fun testFitnessFunctionCreation() {
        val ff = FitnessFunction(
            name = "performance",
            description = "Code must be within 10x of reference implementation",
            type = FitnessFunctionType.PERFORMANCE.name,
            threshold = "10x of reference"
        )

        assertEquals("performance", ff.name)
        assertEquals(FitnessFunctionType.PERFORMANCE.name, ff.type)
        assertNotNull(ff.threshold)
    }

    @Test
    fun testFitnessFunctionDefaults() {
        val ff = FitnessFunction(
            name = "algorithm-complexity",
            description = "Critical paths must use O(log n) lookups",
            type = FitnessFunctionType.ALGORITHM_COMPLEXITY.name
        )

        assertEquals(null, ff.threshold)
        assertEquals("", ff.rationale)
    }

    // ============= FitnessFunctionResult Tests =============

    @Test
    fun testFitnessFunctionResultPassed() {
        val ff = FitnessFunction(
            name = "dependency-count",
            description = "Solution must not add unnecessary dependencies",
            type = FitnessFunctionType.DEPENDENCY_COUNT.name,
            threshold = "< 10 dependencies"
        )

        val result = FitnessFunctionResult(
            function = ff,
            passed = true,
            score = 1.0,
            actualValue = "3 dependencies",
            expectedValue = "< 10 dependencies",
            evidence = listOf("package.json has 3 direct dependencies"),
            recommendation = ""
        )

        assertTrue(result.passed)
        assertEquals(1.0, result.score)
        assertTrue(result.evidence.isNotEmpty())
    }

    @Test
    fun testFitnessFunctionResultFailed() {
        val ff = FitnessFunction(
            name = "algorithm-complexity",
            description = "Primary key lookup must use B-tree search",
            type = FitnessFunctionType.ALGORITHM_COMPLEXITY.name,
            threshold = "O(log n) B-tree search for primary key lookups"
        )

        val result = FitnessFunctionResult(
            function = ff,
            passed = false,
            score = 0.0,
            actualValue = "O(n) full table scan",
            expectedValue = "O(log n) B-tree search for primary key lookups",
            evidence = listOf("is_rowid_ref() only checks 'rowid', '_rowid_', 'oid' - misses INTEGER PRIMARY KEY alias"),
            recommendation = "Add is_ipk check: when column is flagged as is_ipk=true, treat it as XN_ROWID for B-tree search"
        )

        assertFalse(result.passed)
        assertEquals(0.0, result.score)
        assertTrue(result.recommendation.isNotEmpty())
    }

    // ============= FitnessReport Tests =============

    @Test
    fun testFitnessReportAllPassed() {
        val ff1 = FitnessFunction("perf", "Performance check", FitnessFunctionType.PERFORMANCE.name)
        val ff2 = FitnessFunction("deps", "Dependency count", FitnessFunctionType.DEPENDENCY_COUNT.name)

        val report = FitnessReport(
            projectPath = "/project",
            totalFunctions = 2,
            passedFunctions = 2,
            failedFunctions = 0,
            overallScore = 1.0,
            results = listOf(
                FitnessFunctionResult(ff1, true, 1.0, "5ms", "< 100ms"),
                FitnessFunctionResult(ff2, true, 1.0, "3 deps", "< 10 deps")
            ),
            summary = "All fitness functions passed"
        )

        assertTrue(report.passed)
        assertEquals(2, report.totalFunctions)
        assertEquals(2, report.passedFunctions)
        assertEquals(0, report.failedFunctions)
    }

    @Test
    fun testFitnessReportWithFailures() {
        val ff1 = FitnessFunction("perf", "Performance check", FitnessFunctionType.PERFORMANCE.name)
        val ff2 = FitnessFunction("algo", "Algorithm complexity", FitnessFunctionType.ALGORITHM_COMPLEXITY.name)

        val report = FitnessReport(
            projectPath = "/db-project",
            totalFunctions = 2,
            passedFunctions = 0,
            failedFunctions = 2,
            overallScore = 0.0,
            results = listOf(
                FitnessFunctionResult(ff1, false, 0.0, "1815ms", "< 1ms (10x of SQLite 0.09ms)"),
                FitnessFunctionResult(ff2, false, 0.0, "O(n^2) full scan", "O(log n) B-tree")
            ),
            summary = "Code produces plausible output but fails correctness criteria",
            actionItems = listOf(
                "Add is_ipk check to query planner",
                "Use fdatasync instead of fsync for autocommit"
            )
        )

        assertFalse(report.passed)
        assertEquals(2, report.failedFunctions)
        assertEquals(2, report.actionItems.size)
    }

    // ============= FitnessFunctionContext Tests =============

    @Test
    fun testFitnessFunctionContextDefaults() {
        val context = FitnessFunctionContext(projectPath = "./src")

        assertEquals("./src", context.projectPath)
        assertTrue(context.fitnessFunctions.isEmpty())
        assertEquals("", context.context)
    }

    @Test
    fun testFitnessFunctionContextWithFunctions() {
        val context = FitnessFunctionContext(
            projectPath = "/my/project",
            fitnessFunctions = listOf("performance", "algorithm-complexity"),
            context = "SQLite reimplementation in Rust"
        )

        assertEquals(2, context.fitnessFunctions.size)
        assertTrue(context.context.contains("SQLite"))
    }

    // ============= FitnessFunctionType Tests =============

    @Test
    fun testFitnessFunctionTypeValues() {
        val types = FitnessFunctionType.entries
        assertTrue(types.contains(FitnessFunctionType.PERFORMANCE))
        assertTrue(types.contains(FitnessFunctionType.ALGORITHM_COMPLEXITY))
        assertTrue(types.contains(FitnessFunctionType.DEPENDENCY_COUNT))
        assertTrue(types.contains(FitnessFunctionType.CODE_VOLUME))
        assertTrue(types.contains(FitnessFunctionType.API_CORRECTNESS))
        assertTrue(types.contains(FitnessFunctionType.TEST_COVERAGE))
        assertTrue(types.contains(FitnessFunctionType.ARCHITECTURE))
        assertTrue(types.contains(FitnessFunctionType.CUSTOM))
    }

    // ============= Schema Tests =============

    @Test
    fun testFitnessFunctionAgentSchemaExampleUsage() {
        val example = FitnessFunctionAgentSchema.getExampleUsage("fitness-agent")
        assertTrue(example.contains("fitness-agent"))
        assertTrue(example.contains("projectPath"))
        assertTrue(example.contains("fitnessFunctions"))
    }

    @Test
    fun testFitnessFunctionAgentSchemaHasRequiredProperties() {
        val schema = FitnessFunctionAgentSchema
        assertTrue(schema.description.contains("fitness functions"))
    }
}
