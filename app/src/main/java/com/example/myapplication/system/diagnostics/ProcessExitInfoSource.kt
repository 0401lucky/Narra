package com.example.myapplication.system.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

interface ProcessExitInfoSource {
    val supportStatus: HistoricalExitQuerySupport

    fun query(maxRecords: Int): ProcessExitQueryResult
}

fun interface Api30ProcessExitInfoReader {
    fun read(maxRecords: Int): List<RawProcessExitRecord>
}

class AndroidProcessExitInfoSource(
    private val sdkInt: Int,
    private val api30Reader: Api30ProcessExitInfoReader,
) : ProcessExitInfoSource {
    constructor(context: Context) : this(
        sdkInt = Build.VERSION.SDK_INT,
        api30Reader = createReaderForCurrentSdk(context.applicationContext),
    )

    override val supportStatus: HistoricalExitQuerySupport =
        if (sdkInt >= Build.VERSION_CODES.R) {
            HistoricalExitQuerySupport.SUPPORTED
        } else {
            HistoricalExitQuerySupport.UNSUPPORTED
        }

    override fun query(maxRecords: Int): ProcessExitQueryResult {
        require(maxRecords > 0) { "历史退出查询条数必须大于 0" }
        if (supportStatus == HistoricalExitQuerySupport.UNSUPPORTED) {
            return ProcessExitQueryResult.Unsupported
        }
        return ProcessExitQueryResult.Success(api30Reader.read(maxRecords))
    }

    private companion object {
        fun createReaderForCurrentSdk(context: Context): Api30ProcessExitInfoReader {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                createApi30Reader(context)
            } else {
                Api30ProcessExitInfoReader { emptyList() }
            }
        }

        @RequiresApi(Build.VERSION_CODES.R)
        fun createApi30Reader(context: Context): Api30ProcessExitInfoReader {
            return AndroidApi30ProcessExitInfoReader(context)
        }
    }
}

internal fun api30ReaderForSdk(
    sdkInt: Int,
    supportedReader: () -> Api30ProcessExitInfoReader,
): Api30ProcessExitInfoReader {
    return if (sdkInt >= Build.VERSION_CODES.R) {
        supportedReader()
    } else {
        Api30ProcessExitInfoReader { emptyList() }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private class AndroidApi30ProcessExitInfoReader(
    context: Context,
) : Api30ProcessExitInfoReader {
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val packageName = context.packageName

    override fun read(maxRecords: Int): List<RawProcessExitRecord> {
        return activityManager
            .getHistoricalProcessExitReasons(packageName, 0, maxRecords)
            .map { exitInfo ->
                RawProcessExitRecord(
                    reasonCode = exitInfo.reason,
                    timestampEpochMillis = exitInfo.timestamp,
                    importance = exitInfo.importance,
                    status = exitInfo.status,
                    pssKb = exitInfo.pss,
                    rssKb = exitInfo.rss,
                )
            }
    }
}
