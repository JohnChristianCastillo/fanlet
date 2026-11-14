package dev.johncastles.fanlet.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigCodecTest {

    @Test
    fun roundTrip_keepsEverything() {
        val config = FanletConfig(
            showBars = true,
            bars = listOf(
                BarConfig(
                    id = "left",
                    edge = Edge.LEFT,
                    enabled = false,
                    thicknessDp = 32,
                    lengthFraction = 0.6f,
                    centerFraction = 0.25f,
                    hapticsEnabled = false,
                    slices = listOf(SliceConfig("org.example.mail", secondaryPackage = "org.example.chat")),
                ),
                BarConfig(id = "right", edge = Edge.RIGHT),
            ),
        )
        assertEquals(config, ConfigCodec.decode(ConfigCodec.encode(config)))
    }

    @Test
    fun defaults_roundTrip() {
        assertEquals(FanletConfig.DEFAULT, ConfigCodec.decode(ConfigCodec.encode(FanletConfig.DEFAULT)))
    }

    @Test
    fun malformedJson_isRejected() {
        assertNull(ConfigCodec.decode("{ not json"))
        assertNull(ConfigCodec.decode(""))
    }

    @Test
    fun wrongTypes_areRejected() {
        assertNull(ConfigCodec.decode("""{"schemaVersion": 1, "showBars": "yes"}"""))
        assertNull(ConfigCodec.decode("""{"schemaVersion": 1, "bars": [{"id": "x", "edge": "TOP"}]}"""))
    }

    @Test
    fun unknownSchemaVersions_areRejected() {
        assertNull(ConfigCodec.decode("""{"schemaVersion": 0}"""))
        assertNull(ConfigCodec.decode("""{"schemaVersion": ${FanletConfig.CURRENT_SCHEMA_VERSION + 1}}"""))
    }

    @Test
    fun missingFields_getDefaults_unknownFieldsAreIgnored() {
        val decoded = ConfigCodec.decode("""{"schemaVersion": 1, "futureSetting": 42}""")
        assertEquals(FanletConfig.DEFAULT, decoded)
    }

    @Test
    fun outOfRangeValues_areClamped() {
        val json = """
            {"schemaVersion": 1, "bars": [
              {"id": "left", "edge": "LEFT", "thicknessDp": 500, "lengthFraction": 0.0, "centerFraction": 7.5}
            ]}
        """.trimIndent()
        val bar = ConfigCodec.decode(json)!!.bars.single()
        assertEquals(BarConfig.MAX_THICKNESS_DP, bar.thicknessDp)
        assertEquals(BarConfig.MIN_LENGTH_FRACTION, bar.lengthFraction)
        assertEquals(1f, bar.centerFraction)
    }

    @Test
    fun emptyBarList_getsDefaultBars() {
        val decoded = ConfigCodec.decode("""{"schemaVersion": 1, "bars": []}""")!!
        assertEquals(FanletConfig.defaultBars(), decoded.bars)
    }

    @Test
    fun nanFraction_becomesDefault() {
        val bar = BarConfig(id = "x", edge = Edge.LEFT, lengthFraction = Float.NaN).normalized()
        assertEquals(BarConfig.DEFAULT_LENGTH_FRACTION, bar.lengthFraction)
    }
}
