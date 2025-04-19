package dev.hossain.trmnl.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.anvil.annotations.optional.SingleIn
import dev.hossain.trmnl.di.AppScope
import dev.hossain.trmnl.di.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

private val Context.imageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "trmnl_image_metadata",
)

@SingleIn(AppScope::class)
class ImageMetadataStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private val IMAGE_URL_KEY = stringPreferencesKey("last_image_url")
            private val TIMESTAMP_KEY = longPreferencesKey("last_image_timestamp")
            private val REFRESH_RATE_KEY = longPreferencesKey("last_refresh_rate")
        }

        /**
         * Get the image metadata as a Flow
         */
        val imageMetadataFlow: Flow<ImageMetadata?> =
            context.imageDataStore.data.map { preferences ->
                val url = preferences[IMAGE_URL_KEY] ?: return@map null
                val timestamp = preferences[TIMESTAMP_KEY] ?: Instant.now().toEpochMilli()
                val refreshRate = preferences[REFRESH_RATE_KEY]

                ImageMetadata(url, timestamp, refreshRate)
            }

        /**
         * Save new image metadata
         */
        suspend fun saveImageMetadata(
            imageUrl: String,
            refreshRateSecs: Long? = null,
        ) {
            Timber.d("Saving image metadata: url=$imageUrl, refreshRate=$refreshRateSecs")
            context.imageDataStore.edit { preferences ->
                preferences[IMAGE_URL_KEY] = imageUrl
                preferences[TIMESTAMP_KEY] = Instant.now().toEpochMilli()
                refreshRateSecs?.let { preferences[REFRESH_RATE_KEY] = it }
            }
        }

        /**
         * Clear stored image metadata
         */
        suspend fun clearImageMetadata() {
            context.imageDataStore.edit { preferences ->
                preferences.remove(IMAGE_URL_KEY)
                preferences.remove(TIMESTAMP_KEY)
                preferences.remove(REFRESH_RATE_KEY)
            }
        }
    }
