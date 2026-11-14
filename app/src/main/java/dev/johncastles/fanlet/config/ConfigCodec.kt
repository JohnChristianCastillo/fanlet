package dev.johncastles.fanlet.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Converts [FanletConfig] to and from JSON. Pure Kotlin, so it is covered by JVM unit tests. */
object ConfigCodec {

    /** Why a text could not be read as a config. */
    enum class ParseError {
        /** Not JSON at all. */
        NOT_JSON,

        /** JSON, but not a Fanlet config (no top-level object with a `schemaVersion`). */
        NOT_A_FANLET_CONFIG,

        /** Written by a newer version of Fanlet that this version does not understand. */
        NEWER_VERSION,

        /** A Fanlet config with missing structure or values of the wrong type. */
        INVALID_CONTENT,
    }

    sealed interface ParseResult {
        data class Success(val config: FanletConfig) : ParseResult
        data class Failure(val error: ParseError) : ParseResult
    }

    private val json = Json {
        // Fields added by newer versions of the same schema are skipped, missing ones get defaults.
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(config: FanletConfig): String = json.encodeToString(FanletConfig.serializer(), config)

    /**
     * Parses [text] and says why if it is not a usable config. A successful result is
     * migrated to the current schema and has every value clamped to its allowed range.
     */
    fun parse(text: String): ParseResult {
        val root = try {
            json.parseToJsonElement(text)
        } catch (e: IllegalArgumentException) {
            // SerializationException is a subclass.
            return ParseResult.Failure(ParseError.NOT_JSON)
        }
        val obj = root as? JsonObject ?: return ParseResult.Failure(ParseError.NOT_A_FANLET_CONFIG)
        val version = (obj["schemaVersion"] as? JsonPrimitive)?.intOrNull
            ?: return ParseResult.Failure(ParseError.NOT_A_FANLET_CONFIG)
        when {
            version > FanletConfig.CURRENT_SCHEMA_VERSION -> return ParseResult.Failure(ParseError.NEWER_VERSION)
            version < 1 -> return ParseResult.Failure(ParseError.INVALID_CONTENT)
        }

        val config = try {
            json.decodeFromJsonElement(FanletConfig.serializer(), migrate(obj, version))
        } catch (e: IllegalArgumentException) {
            return ParseResult.Failure(ParseError.INVALID_CONTENT)
        }
        return ParseResult.Success(config.normalized())
    }

    /** Like [parse], but only the config: null for anything that is not a usable config. */
    fun decode(text: String): FanletConfig? = (parse(text) as? ParseResult.Success)?.config

    /**
     * Upgrades the JSON of an older schema, one version at a time, before it is decoded.
     * Works on the JSON tree because older fields may no longer exist in the model.
     */
    private fun migrate(obj: JsonObject, version: Int): JsonObject {
        var current = obj
        if (version < 2) current = migrateV1ToV2(current)
        return current
    }

    /**
     * Version 1 kept a slice list per bar; version 2 has one list shared by every bar.
     * Keeps the list of the first bar that has slices. The old per-bar lists stay in the
     * JSON and are ignored as unknown keys.
     */
    private fun migrateV1ToV2(obj: JsonObject): JsonObject {
        val sharedSlices = (obj["bars"] as? JsonArray)
            ?.mapNotNull { bar -> (bar as? JsonObject)?.get("slices") as? JsonArray }
            ?.firstOrNull { it.isNotEmpty() }
            ?: JsonArray(emptyList())
        return JsonObject(obj + ("slices" to sharedSlices) + ("schemaVersion" to JsonPrimitive(2)))
    }
}
