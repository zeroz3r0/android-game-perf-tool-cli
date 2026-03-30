package com.gameperf.analysis

import com.gameperf.core.PercentileStats
import com.gameperf.core.Problem

/**
 * Calculates a performance grade (A-F) based on FPS percentiles
 * and detected problems. Scoring is transparent and deterministic.
 *
 * Scoring breakdown:
 *   Base: 100 points
 *   FPS penalty: 0-40 points based on average FPS
 *   P1 penalty: 0-15 points based on worst-case FPS
 *   Problem penalties: HIGH=-12, MEDIUM=-6, LOW=-2 each
 *
 * Grade thresholds: A>=85, B>=70, C>=55, D>=40, F<40
 */
object GradeCalculator {

    data class GradeBreakdown(
        val grade: Char,
        val score: Double,
        val fpsPenalty: Int,
        val p1Penalty: Int,
        val problemPenalty: Int
    )

    fun calculate(fpsPercentiles: PercentileStats?, problems: List<Problem>): Char {
        return breakdown(fpsPercentiles, problems).grade
    }

    fun breakdown(fpsPercentiles: PercentileStats?, problems: List<Problem>): GradeBreakdown {
        var score = 100.0
        val avgFps = fpsPercentiles?.avg ?: 0.0

        val fpsPenalty = when {
            avgFps >= 55 -> 0
            avgFps >= 45 -> 10
            avgFps >= 35 -> 20
            avgFps > 0 -> 35
            else -> 40
        }
        score -= fpsPenalty

        val p1 = fpsPercentiles?.p1 ?: 0.0
        val p1Penalty = when {
            p1 < 20 -> 15
            p1 < 30 -> 8
            else -> 0
        }
        score -= p1Penalty

        var problemPenalty = 0
        for (problem in problems) {
            problemPenalty += problem.severity.weight
        }
        score -= problemPenalty

        val grade = when {
            score >= 85 -> 'A'
            score >= 70 -> 'B'
            score >= 55 -> 'C'
            score >= 40 -> 'D'
            else -> 'F'
        }

        return GradeBreakdown(
            grade = grade,
            score = score.coerceAtLeast(0.0),
            fpsPenalty = fpsPenalty,
            p1Penalty = p1Penalty,
            problemPenalty = problemPenalty
        )
    }
}
