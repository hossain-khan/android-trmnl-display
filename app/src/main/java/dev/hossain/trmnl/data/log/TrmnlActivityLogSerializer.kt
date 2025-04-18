package dev.hossain.trmnl.data.log

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.squareup.anvil.annotations.optional.SingleIn
import com.squareup.moshi.Moshi
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.di.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

object TrmnlActivityLogSerializer : Serializer<TrmnlActivityLogs> {
    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(TrmnlActivityLogs::class.java)

    override val defaultValue: TrmnlActivityLogs
        get() = TrmnlActivityLogs(emptyList())

    override suspend fun readFrom(input: InputStream): TrmnlActivityLogs =
        withContext(Dispatchers.IO) {
            try {
                val jsonString = input.readBytes().decodeToString()
                adapter.fromJson(jsonString) ?: defaultValue
            } catch (e: Exception) {
                Timber.e(e, "Error reading activity logs")
                defaultValue
            }
        }

    override suspend fun writeTo(
        t: TrmnlActivityLogs,
        output: OutputStream,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val jsonString = adapter.toJson(t)
                output.write(jsonString.toByteArray())
            } catch (e: Exception) {
                Timber.e(e, "Error writing activity logs")
            }
        }
    }
}

@SingleIn(AppScope::class)
class TrmnlActivityLogManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dataStore: DataStore<TrmnlActivityLogs>,
    ) {
        companion object {
            const val MAX_LOG_ENTRIES = 100
        }

        val logsFlow: Flow<List<TrmnlActivityLog>> =
            dataStore.data
                .catch { e ->
                    Timber.e(e, "Error reading logs")
                    emit(TrmnlActivityLogs())
                }.map { it.logs }

        suspend fun addSuccessLog(
            imageUrl: String,
            refreshRateSeconds: Long?,
        ) {
            addLog(TrmnlActivityLog.createSuccess(imageUrl, refreshRateSeconds))
        }

        suspend fun addFailureLog(error: String) {
            addLog(TrmnlActivityLog.createFailure(error))
        }

        private suspend fun addLog(log: TrmnlActivityLog) {
            dataStore.updateData { currentLogs ->
                val updatedLogs =
                    currentLogs.logs.toMutableList().apply {
                        add(0, log) // Add to the beginning for descending order
                        if (size > MAX_LOG_ENTRIES) {
                            // Keep only the most recent logs
                            removeAll(subList(MAX_LOG_ENTRIES, size))
                        }
                    }
                TrmnlActivityLogs(updatedLogs)
            }
        }

        suspend fun clearLogs() {
            dataStore.updateData {
                TrmnlActivityLogs(emptyList())
            }
        }
    }
