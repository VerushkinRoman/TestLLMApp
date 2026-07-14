package com.llmapp.pr_review

data class PullRequestInfo(
    val number: Int,
    val title: String,
    val description: String,
    val author: String,
    val baseBranch: String,
    val headBranch: String,
    val state: String,
    val createdAt: String,
    val updatedAt: String,
)

data class ChangedFile(
    val path: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val patch: String,
)

data class PullRequestDiff(
    val pr: PullRequestInfo,
    val files: List<ChangedFile>,
    val diffText: String,
    val totalAdditions: Int,
    val totalDeletions: Int,
    val totalChanges: Int,
)

data class ReviewIssue(
    val severity: ReviewSeverity,
    val category: ReviewCategory,
    val title: String,
    val description: String,
    val filePath: String? = null,
    val lineNumber: Int? = null,
    val suggestion: String? = null,
)

enum class ReviewSeverity { CRITICAL, WARNING, INFO, SUGGESTION }

enum class ReviewCategory {
    BUG,
    ARCHITECTURE,
    PERFORMANCE,
    SECURITY,
    CODE_STYLE,
    TEST_COVERAGE,
    BEST_PRACTICE,
    POTENTIAL_ISSUE,
}

data class ReviewReport(
    val prInfo: PullRequestInfo,
    val issues: List<ReviewIssue>,
    val summary: String,
    val positiveHighlights: List<String>,
    val recommendations: List<String>,
    val overallScore: Int,
    val reviewTimestamp: Long = System.currentTimeMillis(),
)

data class ReviewConfig(
    val model: String = "mistral/mistral-large-latest",
    val reviewDepth: ReviewDepth = ReviewDepth.FULL,
    val focusAreas: List<ReviewCategory> = emptyList(),
    val maxDiffSize: Int = 50_000,
)

enum class ReviewDepth { QUICK, FOCUSED, FULL }
