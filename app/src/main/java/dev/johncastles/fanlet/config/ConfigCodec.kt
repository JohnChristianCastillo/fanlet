package dev.johncastles.fanlet.config

import kotlinx.serialization.json.Json

/** Converts [FanletConfig] to and from JSON. Pure Kotlin, so it is covered by JVM unit tests. */
object ConfigCodec {

    private val json = Json {
        // Fields added by newer versions of the same schema are skipped, missing ones get defaults.
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(config: FanletConfig): String = json.encodeToString(FanletConfig.serializer(), config)

    /**
     * Parses [text]. Returns null if it is not a valid config: malformed JSON, wrong types,
     * or a schema version this app does not know (zero or less, or written by a newer app).
     */
    fun decode(text: String): FanletConfig? {
        val config = try {
            json.decodeFromString(FanletConfig.serializer(), text)
        } catch (e: IllegalArgumentException) {
            // SerializationException is a subclass.
            return null
        }
        if (config.schemaVersion !in 1..FanletConfig.CURRENT_SCHEMA_VERSION) return null
        return migrate(config).normalized()
    }

    /** Upgrades an older schema to the current one. Version 1 is the only version so far. */
    private fun migrate(config: FanletConfig): FanletConfig =
        config.copy(schemaVersion = FanletConfig.CURRENT_SCHEMA_VERSION)
}
