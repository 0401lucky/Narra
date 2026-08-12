package com.example.myapplication.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.myapplication.model.AppSettings
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsStoreTest {
    @Test
    fun settingsFlow_multipleCollectorsParseEachEmissionOnceAndReplayLatest() = runTest {
        val dataStore = MutablePreferencesDataStore(replay = 1)
        val secureStore = RecordingSecureValueStore()
        var parseCount = 0
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val sharingScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val store = AppSettingsStore(
            dataStore = dataStore,
            secureValueStore = secureStore,
            sharingScope = sharingScope,
            settingsDispatcher = testDispatcher,
            parseObserver = { parseCount += 1 },
            readRetryDelayMillis = 0L,
        )

        try {
            advanceUntilIdle()
            val firstEmissions = mutableListOf<AppSettings>()
            val secondEmissions = mutableListOf<AppSettings>()
            val firstCollector = launch {
                store.settingsFlow.take(2).toList(firstEmissions)
            }
            val secondCollector = launch {
                store.settingsFlow.take(2).toList(secondEmissions)
            }
            advanceUntilIdle()
            dataStore.emit(emptyPreferences())
            val themeKey = stringPreferencesKey("theme_mode")
            dataStore.emit(mutablePreferencesOf(themeKey to "dark"))
            advanceUntilIdle()

            assertEquals(2, firstEmissions.size)
            assertEquals(2, secondEmissions.size)
            assertEquals(2, parseCount)
            assertEquals(8, secureStore.readCount)
            val replayReads = List(3) {
                store.settingsFlow.first().themeMode.storageValue
            }
            assertEquals(listOf("dark", "dark", "dark"), replayReads)
            assertEquals(2, parseCount)
            assertEquals(8, secureStore.readCount)
            firstCollector.join()
            secondCollector.join()
        } finally {
            sharingScope.cancel()
        }
    }

    @Test
    fun settingsFlow_ioFailureEmitsDefaultThenRecoversFromLaterValue() = runTest {
        val expectedFailure = IOException("read failed")
        val preferences = mutablePreferencesOf(stringPreferencesKey("theme_mode") to "dark")
        val dataStore = SequencedPreferencesDataStore(
            sequence = listOf(
                { flow { throw expectedFailure } },
                { flow { emit(preferences) } },
            ),
        )
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val sharingScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val store = AppSettingsStore(
            dataStore = dataStore,
            secureValueStore = RecordingSecureValueStore(),
            sharingScope = sharingScope,
            settingsDispatcher = testDispatcher,
            readRetryDelayMillis = 0L,
        )

        try {
            val emissions = store.settingsFlow.take(2).toList()

            assertEquals("system", emissions.first().themeMode.storageValue)
            assertEquals("dark", emissions.last().themeMode.storageValue)
            assertEquals(2, dataStore.subscriptionCount)
        } finally {
            sharingScope.cancel()
        }
    }

    @Test
    fun settingsFlow_nonIoFailureReportsUnavailableWithoutDefaultSnapshot() = runTest {
        val expectedFailure = IllegalStateException("programming failure")
        var unavailable: Throwable? = null
        val dataStore = SequencedPreferencesDataStore(
            sequence = listOf({ flow { throw expectedFailure } }),
        )
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val sharingScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val store = AppSettingsStore(
            dataStore = dataStore,
            secureValueStore = RecordingSecureValueStore(),
            sharingScope = sharingScope,
            settingsDispatcher = testDispatcher,
            unavailableObserver = { unavailable = it },
            readRetryDelayMillis = 0L,
        )

        try {
            advanceUntilIdle()

            assertSame(expectedFailure, unavailable)
            assertTrue((store.settingsFlow as SharedFlow).replayCache.isEmpty())
        } finally {
            sharingScope.cancel()
        }
    }

    @Test
    fun settingsFlow_parsesOnInjectedBackgroundDispatcher() = runTest {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "settings-parser-test")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        var parserThread = ""
        val secureStore = RecordingSecureValueStore()
        val sharingScope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val dataStore = MutablePreferencesDataStore(replay = 1)
            val store = AppSettingsStore(
                dataStore = dataStore,
                secureValueStore = secureStore,
                sharingScope = sharingScope,
                settingsDispatcher = dispatcher,
                parseObserver = { parserThread = it },
                readRetryDelayMillis = 0L,
            )

            dataStore.emit(emptyPreferences())
            store.settingsFlow.take(1).toList()

            assertTrue(parserThread.startsWith("settings-parser-test"))
            assertFalse(parserThread.contains("main", ignoreCase = true))
            assertEquals(1, secureStore.readThreadNames.size)
            assertTrue(
                secureStore.readThreadNames.all { threadName ->
                    threadName.startsWith("settings-parser-test") &&
                        !threadName.contains("main", ignoreCase = true)
                },
            )
        } finally {
            sharingScope.cancel()
            dispatcher.close()
            executor.shutdownNow()
        }
    }
}

private class MutablePreferencesDataStore(
    replay: Int = 0,
) : DataStore<Preferences> {
    private val upstream = MutableSharedFlow<Preferences>(replay = replay)
    override val data: Flow<Preferences> = upstream

    suspend fun emit(preferences: Preferences) {
        upstream.emit(preferences)
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        error("本测试不应写入 DataStore")
    }
}

private class SequencedPreferencesDataStore(
    private val sequence: List<() -> Flow<Preferences>>,
) : DataStore<Preferences> {
    var subscriptionCount: Int = 0
        private set

    override val data: Flow<Preferences>
        get() {
            val index = subscriptionCount.coerceAtMost(sequence.lastIndex)
            subscriptionCount += 1
            return sequence[index]()
        }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        error("本测试不应写入 DataStore")
    }
}

private class RecordingSecureValueStore : SettingsSecureValueStore {
    var readCount: Int = 0
        private set
    val readThreadNames = linkedSetOf<String>()

    override fun getString(key: String): String {
        readCount += 1
        readThreadNames += Thread.currentThread().name
        return ""
    }

    override fun putString(key: String, value: String) = Unit

    override fun getStringMap(key: String): Map<String, String> {
        readCount += 1
        readThreadNames += Thread.currentThread().name
        return emptyMap()
    }

    override fun putStringMap(key: String, values: Map<String, String>) = Unit
}
