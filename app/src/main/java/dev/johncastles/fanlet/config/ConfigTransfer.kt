package dev.johncastles.fanlet.config

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Exports and imports the config as a JSON file the user picks through the system file
 * picker (Storage Access Framework). Fanlet only ever touches that one file, and needs no
 * storage permission for it.
 */
object ConfigTransfer {

    const val SUGGESTED_FILE_NAME = "fanlet-settings.json"
    const val MIME_TYPE = "application/json"

    /** Larger than any real config by far; anything bigger is not a Fanlet settings file. */
    private const val MAX_IMPORT_BYTES = 256 * 1024

    private const val TAG = "ConfigTransfer"

    sealed interface ImportResult {
        data class Success(val config: FanletConfig) : ImportResult
        data object ReadFailed : ImportResult
        data object TooLarge : ImportResult
        data class Invalid(val error: ConfigCodec.ParseError) : ImportResult
    }

    /** Writes [config] to [uri]. Returns false if the file could not be written. */
    suspend fun export(resolver: ContentResolver, uri: Uri, config: FanletConfig): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val stream = resolver.openOutputStream(uri, "w") ?: return@withContext false
                stream.use { it.write(ConfigCodec.encode(config).encodeToByteArray()) }
                true
            } catch (e: IOException) {
                Log.w(TAG, "Export failed", e)
                false
            } catch (e: SecurityException) {
                Log.w(TAG, "Export failed", e)
                false
            }
        }

    /** Reads and validates the config in [uri]. Nothing is applied; the caller decides. */
    suspend fun import(resolver: ContentResolver, uri: Uri): ImportResult =
        withContext(Dispatchers.IO) {
            val bytes = try {
                val stream = resolver.openInputStream(uri) ?: return@withContext ImportResult.ReadFailed
                stream.use { it.readAtMost(MAX_IMPORT_BYTES) } ?: return@withContext ImportResult.TooLarge
            } catch (e: IOException) {
                Log.w(TAG, "Import failed", e)
                return@withContext ImportResult.ReadFailed
            } catch (e: SecurityException) {
                Log.w(TAG, "Import failed", e)
                return@withContext ImportResult.ReadFailed
            }
            when (val parsed = ConfigCodec.parse(bytes.decodeToString())) {
                is ConfigCodec.ParseResult.Success -> ImportResult.Success(parsed.config)
                is ConfigCodec.ParseResult.Failure -> ImportResult.Invalid(parsed.error)
            }
        }
}
