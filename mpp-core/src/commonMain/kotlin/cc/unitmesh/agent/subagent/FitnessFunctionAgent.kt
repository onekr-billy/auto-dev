package cc.unitmesh.agent.subagent

import cc.unitmesh.agent.core.SubAgent
import cc.unitmesh.agent.model.AgentDefinition
import cc.unitmesh.agent.model.PromptConfig
import cc.unitmesh.agent.model.RunConfig
import cc.unitmesh.agent.tool.ToolResult
import cc.unitmesh.agent.tool.schema.DeclarativeToolSchema
import cc.unitmesh.agent.tool.schema.SchemaPropertyBuilder.array
import cc.unitmesh.agent.tool.schema.SchemaPropertyBuilder.string
import cc.unitmesh.llm.LLMService
import cc.unitmesh.llm.ModelConfig
import kotlinx.serialization.Serializable

// ============= Schema =============

object FitnessFunctionAgentSchema : DeclarativeToolSchema(
    description = "Evaluate code against architect fitness functions to verify correctness beyond plausibility",
    properties = mapOf(
        "projectPath" to string(
            description = "Path to the project or code to evaluate",
            required = true
        ),
        "fitnessFunctions" to array(
            description = "List of fitness function names to run (e.g. 'performance', 'algorithm-complexity', 'dependency-count'). If empty, runs all applicable functions.",
            itemType = string("Fitness function name"),
            required = false
        ),
        "context" to string(
            description = "Additional context about the code or requirements (optional)",
            required = false
        )
    )
) {
    override fun getExampleUsage(toolName: String): String {
        return "/$toolName projectPath=\"./src\" fitnessFunctions=[\"performance\", \"algorithm-complexity\"] context=\"database implementation\""
    }
}

// ============= Data Models =============

/**
 * Types of fitness functions an architect may define.
 *
 * Inspired by the ThoughtWorks fitness function-driven development approach:
 * https://www.thoughtworks.com/insights/articles/fitness-function-driven-development
 *
 * A fitness function provides an objective measure of how well a solution
 * achieves a given architectural characteristic.
 */
enum class FitnessFunctionType {
    /** Validates time complexity and throughput of algorithms */
    PERFORMANCE,
    /** Validates algorithm correctness (e.g., O(log n) vs O(n) lookup) */
    ALGORITHM_COMPLEXITY,
    /** Validates that dependency count stays within acceptable limits */
    DEPENDENCY_COUNT,
    /** Validates code volume relative to problem complexity */
    CODE_VOLUME,
    /** Validates use of idiomatic platform APIs (e.g., fdatasync vs fsync) */
    API_CORRECTNESS,
    /** Validates test coverage and quality */
    TEST_COVERAGE,
    /** Validates architectural patterns and layer separation */
    ARCHITECTURE,
    /** Custom fitness function defined by the user */
    CUSTOM
}

/**
 * A single fitness function definition.
 *
 * Fitness functions define measurable acceptance criteria that must be met
 * before LLM-generated code is considered correct - not just plausible.
 */
@Serializable
data class FitnessFunction(
    val name: String,
    val description: String,
    val type: String,
    val threshold: String? = null,
    val rationale: String = ""
)

/**
 * Result of evaluating a single fitness function.
 */
@Serializable
data class FitnessFunctionResult(
    val function: FitnessFunction,
    val passed: Boolean,
    val score: Double,
    val actualValue: String,
    val expectedValue: String,
    val evidence: List<String> = emptyList(),
    val recommendation: String = ""
)

/**
 * Summary report produced by the FitnessFunctionAgent.
 */
@Serializable
data class FitnessReport(
    val projectPath: String,
    val totalFunctions: Int,
    val passedFunctions: Int,
    val failedFunctions: Int,
    val overallScore: Double,
    val results: List<FitnessFunctionResult>,
    val summary: String,
    val actionItems: List<String> = emptyList()
) {
    val passed: Boolean get() = failedFunctions == 0
}

/**
 * Input context for the FitnessFunctionAgent.
 */
@Serializable
data class FitnessFunctionContext(
    val projectPath: String,
    val fitnessFunctions: List<String> = emptyList(),
    val context: String = ""
)

// ============= Agent =============

/**
 * FitnessFunctionAgent - Evaluates code against architect fitness functions.
 *
 * Inspired by the concept that LLMs optimize for plausibility, not correctness.
 * This agent applies measurable fitness functions to verify that generated code
 * meets real acceptance criteria - not just looks correct.
 *
 * Key fitness functions include:
 * - **Performance**: Is the code actually fast, or just plausibly fast?
 * - **Algorithm Complexity**: Does the implementation use optimal algorithms?
 * - **Dependency Count**: Does the solution have appropriate scope?
 * - **Code Volume**: Is the solution proportionate to the problem?
 * - **API Correctness**: Does the code use correct platform-specific APIs?
 *
 * Reference: ThoughtWorks fitness function-driven development
 * https://www.thoughtworks.com/insights/articles/fitness-function-driven-development
 */
class FitnessFunctionAgent(
    private val projectPath: String,
    private val llmService: LLMService
) : SubAgent<FitnessFunctionContext, ToolResult.AgentResult>(
    definition = createDefinition()
) {

    override fun validateInput(input: Map<String, Any>): FitnessFunctionContext {
        @Suppress("UNCHECKED_CAST")
        return FitnessFunctionContext(
            projectPath = input["projectPath"] as? String ?: projectPath,
            fitnessFunctions = (input["fitnessFunctions"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            context = input["context"] as? String ?: ""
        )
    }

    override fun getParameterClass(): String = FitnessFunctionContext::class.simpleName ?: "FitnessFunctionContext"

    override suspend fun execute(
        input: FitnessFunctionContext,
        onProgress: (String) -> Unit
    ): ToolResult.AgentResult {
        onProgress("Architect Fitness Function Agent")
        onProgress("Project: ${input.projectPath}")

        val functionsToRun = if (input.fitnessFunctions.isEmpty()) {
            onProgress("No fitness functions specified - running all applicable checks")
            FitnessFunctionType.entries.map { it.name.lowercase().replace('_', '-') }
        } else {
            input.fitnessFunctions
        }

        onProgress("Evaluating ${functionsToRun.size} fitness function(s)...")

        val defaultFunctions = buildDefaultFitnessFunctions(input.context)
        val selectedFunctions = defaultFunctions.filter { ff ->
            functionsToRun.any { requested ->
                ff.name.equals(requested, ignoreCase = true) ||
                        ff.type.equals(requested, ignoreCase = true)
            }
        }.ifEmpty { defaultFunctions }

        val prompt = buildEvaluationPrompt(input, selectedFunctions)

        onProgress("Consulting LLM for fitness function evaluation...")
        val llmResponse = llmService.sendPrompt(prompt)

        val report = parseReport(llmResponse, input.projectPath, selectedFunctions)

        onProgress("Evaluation complete: ${report.passedFunctions}/${report.totalFunctions} fitness functions passed")
        if (!report.passed) {
            onProgress("Action items:")
            report.actionItems.forEach { item -> onProgress("  - $item") }
        }

        return ToolResult.AgentResult(
            success = report.passed,
            content = formatReport(report),
            metadata = mapOf(
                "totalFunctions" to report.totalFunctions.toString(),
                "passedFunctions" to report.passedFunctions.toString(),
                "failedFunctions" to report.failedFunctions.toString(),
                "overallScore" to report.overallScore.toString(),
                "passed" to report.passed.toString()
            )
        )
    }

    private fun buildDefaultFitnessFunctions(context: String): List<FitnessFunction> {
        val contextLower = context.lowercase()
        val isDatabase = contextLower.contains("database") || contextLower.contains("sql") || contextLower.contains("query")

        return buildList {
            add(
                FitnessFunction(
                    name = "performance",
                    description = "Code performance must be within acceptable bounds of a reference implementation",
                    type = FitnessFunctionType.PERFORMANCE.name,
                    threshold = "10x of reference implementation",
                    rationale = "LLMs optimize for plausibility; without a performance fitness function, " +
                            "generated code may be 20,000x slower on basic operations"
                )
            )
            add(
                FitnessFunction(
                    name = "algorithm-complexity",
                    description = "Critical paths must use optimal algorithmic complexity",
                    type = FitnessFunctionType.ALGORITHM_COMPLEXITY.name,
                    threshold = "Use O(log n) for indexed lookups; O(n) is acceptable only for full scans",
                    rationale = "Without checking algorithmic complexity, a query planner may emit O(n) " +
                            "full table scans for indexed lookups that should use O(log n) B-tree searches"
                )
            )
            add(
                FitnessFunction(
                    name = "dependency-count",
                    description = "Solution must not introduce unnecessary dependencies",
                    type = FitnessFunctionType.DEPENDENCY_COUNT.name,
                    threshold = "Dependencies proportionate to problem scope",
                    rationale = "LLM-generated solutions may add 800+ dependencies for problems " +
                            "solvable with a one-line cron job"
                )
            )
            add(
                FitnessFunction(
                    name = "code-volume",
                    description = "Code volume must be proportionate to problem complexity",
                    type = FitnessFunctionType.CODE_VOLUME.name,
                    threshold = "Volume comparable to reference implementations of similar scope",
                    rationale = "576,000 lines for a SQLite reimplementation (3.7x more than SQLite itself) " +
                            "signals over-engineering driven by token generation, not engineering judgement"
                )
            )
            if (isDatabase) {
                add(
                    FitnessFunction(
                        name = "api-correctness",
                        description = "Database code must use correct I/O APIs (fdatasync vs fsync, etc.)",
                        type = FitnessFunctionType.API_CORRECTNESS.name,
                        threshold = "Match reference implementation behavior",
                        rationale = "SQLite uses fdatasync (skipping file metadata sync) for 1.6-2.7x " +
                                "better performance; naive implementations use fsync"
                    )
                )
                add(
                    FitnessFunction(
                        name = "primary-key-lookup",
                        description = "INTEGER PRIMARY KEY columns must trigger indexed B-tree lookups",
                        type = FitnessFunctionType.ALGORITHM_COMPLEXITY.name,
                        threshold = "WHERE id=N must use B-tree search O(log n), not full table scan O(n)",
                        rationale = "Missing ipk check causes O(n) full table scan instead of O(log n) " +
                                "B-tree search; with N lookups on N rows this compounds to O(n^2) total"
                    )
                )
            }
            add(
                FitnessFunction(
                    name = "test-coverage",
                    description = "Critical invariants must have measurable test coverage",
                    type = FitnessFunctionType.TEST_COVERAGE.name,
                    threshold = "Performance invariants must be validated with benchmarks",
                    rationale = "Code that compiles and passes unit tests is not necessarily correct; " +
                            "SQLite has a test suite 590x larger than the library"
                )
            )
        }
    }

    private fun buildEvaluationPrompt(
        input: FitnessFunctionContext,
        functions: List<FitnessFunction>
    ): String {
        val functionsText = functions.joinToString("\n") { ff ->
            """
            Fitness Function: ${ff.name}
            Type: ${ff.type}
            Description: ${ff.description}
            Threshold: ${ff.threshold ?: "Not specified"}
            Rationale: ${ff.rationale}
            """.trimIndent()
        }

        return """
            You are an architect evaluating code against fitness functions.
            
            Fitness functions provide objective measurements of architectural characteristics.
            Unlike unit tests that verify specific behavior, fitness functions verify that the
            system meets broader architectural goals - especially performance and correctness
            invariants that LLM-generated code frequently misses.
            
            Project to evaluate: ${input.projectPath}
            ${if (input.context.isNotEmpty()) "Context: ${input.context}" else ""}
            
            Fitness Functions to evaluate:
            $functionsText
            
            For each fitness function, analyze the codebase at ${input.projectPath} and provide:
            1. Whether it PASSES or FAILS
            2. A score from 0.0 to 1.0
            3. The actual observed value
            4. The expected value based on the threshold
            5. Supporting evidence from the code
            6. A specific recommendation if it fails
            
            Focus on:
            - Algorithmic complexity of critical paths (are indexed lookups O(log n) or O(n)?)
            - Use of appropriate platform APIs vs naive defaults
            - Code volume proportionality (is complexity justified?)
            - Presence of benchmarks validating performance invariants
            
            Respond in this exact format for each fitness function:
            
            FUNCTION: <name>
            PASSED: <true|false>
            SCORE: <0.0-1.0>
            ACTUAL: <observed value>
            EXPECTED: <threshold value>
            EVIDENCE: <specific code evidence, one line>
            RECOMMENDATION: <specific fix if failed, or empty if passed>
            ---
            
            After all functions, provide:
            SUMMARY: <overall assessment>
            ACTION_ITEMS: <comma-separated list of most important fixes>
        """.trimIndent()
    }

    private fun parseReport(
        llmResponse: String,
        path: String,
        functions: List<FitnessFunction>
    ): FitnessReport {
        val results = mutableListOf<FitnessFunctionResult>()
        val functionBlocks = llmResponse.split("---").filter { it.contains("FUNCTION:") }

        for (block in functionBlocks) {
            val name = extractField(block, "FUNCTION") ?: continue
            val matchedFunction = functions.find {
                it.name.equals(name.trim(), ignoreCase = true)
            } ?: FitnessFunction(
                name = name.trim(),
                description = "Evaluated function",
                type = FitnessFunctionType.CUSTOM.name
            )

            val passed = extractField(block, "PASSED")?.trim()?.lowercase() == "true"
            val score = extractField(block, "SCORE")?.trim()?.toDoubleOrNull() ?: if (passed) 1.0 else 0.0
            val actual = extractField(block, "ACTUAL") ?: "Unknown"
            val expected = extractField(block, "EXPECTED") ?: matchedFunction.threshold ?: "Unknown"
            val evidence = extractField(block, "EVIDENCE")?.let { listOf(it.trim()) } ?: emptyList()
            val recommendation = extractField(block, "RECOMMENDATION") ?: ""

            results.add(
                FitnessFunctionResult(
                    function = matchedFunction,
                    passed = passed,
                    score = score,
                    actualValue = actual.trim(),
                    expectedValue = expected.trim(),
                    evidence = evidence,
                    recommendation = recommendation.trim()
                )
            )
        }

        // Fall back to unscored results if LLM didn't follow format
        if (results.isEmpty()) {
            functions.forEach { ff ->
                results.add(
                    FitnessFunctionResult(
                        function = ff,
                        passed = false,
                        score = 0.0,
                        actualValue = "Could not evaluate",
                        expectedValue = ff.threshold ?: "Not specified",
                        evidence = listOf("LLM response did not follow expected format"),
                        recommendation = "Manual evaluation required"
                    )
                )
            }
        }

        val summaryMatch = Regex("SUMMARY:\\s*(.+?)(?=\\nACTION_ITEMS:|$)", RegexOption.DOT_MATCHES_ALL)
            .find(llmResponse)
        val summary = summaryMatch?.groupValues?.get(1)?.trim()
            ?: "Fitness function evaluation completed"

        val actionItemsMatch = Regex("ACTION_ITEMS:\\s*(.+?)$", RegexOption.DOT_MATCHES_ALL)
            .find(llmResponse)
        val actionItems = actionItemsMatch?.groupValues?.get(1)?.trim()
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        val passedCount = results.count { it.passed }
        val overallScore = if (results.isEmpty()) 0.0 else results.sumOf { it.score } / results.size

        return FitnessReport(
            projectPath = path,
            totalFunctions = results.size,
            passedFunctions = passedCount,
            failedFunctions = results.size - passedCount,
            overallScore = overallScore,
            results = results,
            summary = summary,
            actionItems = actionItems
        )
    }

    private fun extractField(text: String, field: String): String? {
        val regex = Regex("$field:\\s*(.+?)(?=\\n[A-Z_]+:|$)", RegexOption.DOT_MATCHES_ALL)
        return regex.find(text)?.groupValues?.get(1)?.trim()
    }

    private fun formatReport(report: FitnessReport): String {
        val sb = StringBuilder()
        sb.appendLine("# Architect Fitness Function Report")
        sb.appendLine()
        sb.appendLine("**Project**: ${report.projectPath}")
        sb.appendLine("**Overall Score**: ${(report.overallScore * 100).toInt()}%")
        sb.appendLine("**Status**: ${if (report.passed) "PASSED" else "FAILED"}")
        sb.appendLine("**Functions**: ${report.passedFunctions}/${report.totalFunctions} passed")
        sb.appendLine()
        sb.appendLine("## Fitness Function Results")
        sb.appendLine()

        report.results.forEach { result ->
            val status = if (result.passed) "PASS" else "FAIL"
            sb.appendLine("### ${result.function.name} [$status]")
            sb.appendLine("- **Score**: ${(result.score * 100).toInt()}%")
            sb.appendLine("- **Expected**: ${result.expectedValue}")
            sb.appendLine("- **Actual**: ${result.actualValue}")
            if (result.evidence.isNotEmpty()) {
                sb.appendLine("- **Evidence**: ${result.evidence.joinToString("; ")}")
            }
            if (result.recommendation.isNotEmpty()) {
                sb.appendLine("- **Recommendation**: ${result.recommendation}")
            }
            sb.appendLine()
        }

        sb.appendLine("## Summary")
        sb.appendLine(report.summary)

        if (report.actionItems.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Action Items")
            report.actionItems.forEach { item ->
                sb.appendLine("- $item")
            }
        }

        return sb.toString()
    }

    companion object {
        private fun createDefinition() = AgentDefinition(
            name = "FitnessFunctionAgent",
            displayName = "Architect Fitness Function Agent",
            description = "Evaluates code against architect fitness functions to verify correctness beyond plausibility. " +
                    "LLMs optimize for plausible code, not correct code. Fitness functions provide measurable acceptance " +
                    "criteria that distinguish working code from merely plausible code.",
            promptConfig = PromptConfig(
                systemPrompt = "You are an architect evaluating code against fitness functions. " +
                        "Fitness functions provide objective measurements of whether code meets architectural goals. " +
                        "Focus on performance invariants, algorithmic complexity, and proportionality of solutions."
            ),
            modelConfig = ModelConfig.default(),
            runConfig = RunConfig(
                maxTurns = 10,
                maxTimeMinutes = 5,
                terminateOnError = false
            )
        )
    }
}
