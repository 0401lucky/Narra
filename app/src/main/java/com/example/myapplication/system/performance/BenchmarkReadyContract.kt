package com.example.myapplication.system.performance

object BenchmarkReadyIds {
    const val FIRST_RUN_CONSENT_READY = "benchmark_ready_first_run_consent"
    const val RETURNING_ROLEPLAY_READY = "benchmark_ready_returning_roleplay"
    const val CHAT_LIST_READY = "benchmark_ready_chat_list"
    const val CHAT_LIST_TOP_READY = "benchmark_ready_chat_list_top"
    const val CHAT_LIST_BOTTOM_READY = "benchmark_ready_chat_list_bottom"
    const val ROLEPLAY_LIST_READY = "benchmark_ready_roleplay_list"
    const val ROLEPLAY_LIST_TOP_READY = "benchmark_ready_roleplay_list_top"
    const val ROLEPLAY_LIST_BOTTOM_READY = "benchmark_ready_roleplay_list_bottom"
    const val ROLEPLAY_STREAM_READY = "benchmark_ready_roleplay_stream"
    const val ROLEPLAY_STREAM_COMPLETE_READY = "benchmark_ready_roleplay_stream_complete"
    const val SETTINGS_READY = "benchmark_ready_settings"
    const val ROLEPLAY_QUICK_SETTINGS_READY = "benchmark_ready_roleplay_quick_settings"
    const val READING_READY = "benchmark_ready_reading"
    const val VOICE_SYNTHESIS_READY = "benchmark_ready_voice_synthesis"
    const val VOICE_PROFILE_READY = "benchmark_ready_voice_profile"

    val ALL: Set<String> = setOf(
        FIRST_RUN_CONSENT_READY,
        RETURNING_ROLEPLAY_READY,
        CHAT_LIST_READY,
        CHAT_LIST_TOP_READY,
        CHAT_LIST_BOTTOM_READY,
        ROLEPLAY_LIST_READY,
        ROLEPLAY_LIST_TOP_READY,
        ROLEPLAY_LIST_BOTTOM_READY,
        ROLEPLAY_STREAM_READY,
        ROLEPLAY_STREAM_COMPLETE_READY,
        SETTINGS_READY,
        ROLEPLAY_QUICK_SETTINGS_READY,
        READING_READY,
        VOICE_SYNTHESIS_READY,
        VOICE_PROFILE_READY,
    )
}

object BenchmarkActionIds {
    const val OPEN_CHAT_HISTORY = "benchmark_action_open_chat_history"
    const val OPEN_SETTINGS = "benchmark_action_open_settings"
    const val OPEN_ROLEPLAY_QUICK_SETTINGS = "benchmark_action_open_roleplay_quick_settings"
    const val OPEN_READING = "benchmark_action_open_reading"
    const val OPEN_VOICE_SYNTHESIS = "benchmark_action_open_voice_synthesis"
    const val OPEN_VOICE_PROFILE = "benchmark_action_open_voice_profile"
    const val CLOSE_VOICE_PROFILE = "benchmark_action_close_voice_profile"
    const val START_OFFLINE_REPLAY = "benchmark_action_start_offline_replay"

    val ALL: Set<String> = setOf(
        OPEN_CHAT_HISTORY,
        OPEN_SETTINGS,
        OPEN_ROLEPLAY_QUICK_SETTINGS,
        OPEN_READING,
        OPEN_VOICE_SYNTHESIS,
        OPEN_VOICE_PROFILE,
        CLOSE_VOICE_PROFILE,
        START_OFFLINE_REPLAY,
    )
}

enum class BenchmarkInitialUiTarget {
    NONE,
    CHAT,
    ROLEPLAY_SESSION,
}

object BenchmarkUiNavigationContract {
    fun initialTarget(scenarioId: BenchmarkScenarioId): BenchmarkInitialUiTarget {
        return when (scenarioId) {
            BenchmarkScenarioId.S0_FIRST_RUN_STARTUP -> BenchmarkInitialUiTarget.NONE
            BenchmarkScenarioId.F1_CHAT_SCROLL -> BenchmarkInitialUiTarget.CHAT
            else -> BenchmarkInitialUiTarget.ROLEPLAY_SESSION
        }
    }

    fun shouldSelectFixedChatConversation(scenarioId: BenchmarkScenarioId?): Boolean {
        return scenarioId == BenchmarkScenarioId.F1_CHAT_SCROLL ||
            scenarioId == BenchmarkScenarioId.P1_BASELINE_PROFILE
    }
}
