package com.example.myapplication.baselineprofile

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceSectionMetric

@OptIn(ExperimentalMetricApi::class)
internal fun BenchmarkScenario.traceMetrics(): List<Metric> {
    return requiredTraceSections.flatMap { section ->
        listOf(
            TraceSectionMetric(
                sectionName = section.name,
                mode = TraceSectionMetric.Mode.First,
                label = section.metricLabel + "Presence",
            ),
            TraceSectionMetric(
                sectionName = section.name,
                mode = TraceSectionMetric.Mode.Count,
                label = section.metricLabel,
            ),
            TraceSectionMetric(
                sectionName = section.name,
                mode = TraceSectionMetric.Mode.Sum,
                label = section.metricLabel + "TotalDuration",
            ),
        )
    }
}
