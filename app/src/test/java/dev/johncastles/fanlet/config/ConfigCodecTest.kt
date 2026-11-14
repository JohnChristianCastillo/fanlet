package dev.johncastles.fanlet.config

import dev.johncastles.fanlet.config.ConfigCodec.ParseError
import dev.johncastles.fanlet.config.ConfigCodec.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigCodecTest {

    @Test
    fun roundTrip_keepsEverything() {
        val config = FanletConfig(
            showBars = true,
            pie = PieAppearance(
                sizeFraction = 0.8f,
                iconSizeDp = 56,
                secondaryIconScale = 0.45f,
                dimBackground = false,
                holdMs = 750,
            ),
            slices = listOf(
                SliceConfig("org.example.mail", secondaryPackage = "org.example.chat"),
                SliceConfig("org.example.maps"),
            ),
            bars = listOf(
                BarConfig(
                    id = "left",
                    edge = Edge.LEFT,
                    enabled = false,
                    thicknessDp = 32,
                    lengthFraction = 0.6f,
                    centerFraction = 0.25f,
                    hapticsEnabled = false,
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
    fun pieAppearance_isClamped() {
        val tooSmall = ConfigCodec.decode(
            """{"schemaVersion": 1, "pie": {"sizeFraction": 0.01, "iconSizeDp": 2, "secondaryIconScale": 0.05}}"""
        )!!.pie
        assertEquals(PieAppearance.MIN_SIZE_FRACTION, tooSmall.sizeFraction)
        assertEquals(PieAppearance.MIN_ICON_SIZE_DP, tooSmall.iconSizeDp)
        assertEquals(PieAppearance.MIN_SECONDARY_ICON_SCALE, tooSmall.secondaryIconScale)

        val tooLarge = ConfigCodec.decode(
            """{"schemaVersion": 1, "pie": {"sizeFraction": 5, "iconSizeDp": 900, "secondaryIconScale": 3}}"""
        )!!.pie
        assertEquals(PieAppearance.MAX_SIZE_FRACTION, tooLarge.sizeFraction)
        assertEquals(PieAppearance.MAX_ICON_SIZE_DP, tooLarge.iconSizeDp)
        assertEquals(PieAppearance.MAX_SECONDARY_ICON_SCALE, tooLarge.secondaryIconScale)
    }

    @Test
    fun configWithoutPieAppearance_getsDefaults() {
        // Files saved before these settings existed.
        val decoded = ConfigCodec.decode("""{"schemaVersion": 1, "showBars": true}""")!!
        assertEquals(PieAppearance(), decoded.pie)
    }

    @Test
    fun holdTime_isClamped() {
        val short = ConfigCodec.decode("""{"schemaVersion": 1, "pie": {"holdMs": 10}}""")!!
        assertEquals(PieAppearance.MIN_HOLD_MS, short.pie.holdMs)
        val long = ConfigCodec.decode("""{"schemaVersion": 1, "pie": {"holdMs": 60000}}""")!!
        assertEquals(PieAppearance.MAX_HOLD_MS, long.pie.holdMs)
    }

    // ---- Import validation: why a file is rejected ------------------------------------

    private fun errorOf(text: String): ParseError? = (ConfigCodec.parse(text) as? ParseResult.Failure)?.error

    @Test
    fun parse_notJson() {
        assertEquals(ParseError.NOT_JSON, errorOf("this is a shopping list"))
        assertEquals(ParseError.NOT_JSON, errorOf(""))
    }

    @Test
    fun parse_jsonThatIsNotAFanletConfig() {
        assertEquals(ParseError.NOT_A_FANLET_CONFIG, errorOf("[1, 2, 3]"))
        assertEquals(ParseError.NOT_A_FANLET_CONFIG, errorOf("""{"name": "something else"}"""))
        assertEquals(ParseError.NOT_A_FANLET_CONFIG, errorOf("""{"schemaVersion": "one"}"""))
    }

    @Test
    fun parse_newerVersion() {
        val newer = FanletConfig.CURRENT_SCHEMA_VERSION + 1
        assertEquals(ParseError.NEWER_VERSION, errorOf("""{"schemaVersion": $newer}"""))
    }

    @Test
    fun parse_damagedContent() {
        assertEquals(ParseError.INVALID_CONTENT, errorOf("""{"schemaVersion": 0}"""))
        assertEquals(ParseError.INVALID_CONTENT, errorOf("""{"schemaVersion": 1, "bars": "left"}"""))
        assertEquals(ParseError.INVALID_CONTENT, errorOf("""{"schemaVersion": 1, "bars": [{"id": "x"}]}"""))
    }

    @Test
    fun parse_exportedConfig_succeeds() {
        val config = FanletConfig.DEFAULT.withSliceAdded("org.example.mail")
        val result = ConfigCodec.parse(ConfigCodec.encode(config))
        assertTrue(result is ParseResult.Success)
        assertEquals(config, (result as ParseResult.Success).config)
    }

    // ---- Migration from version 1 (a slice list per bar) --------------------------------

    @Test
    fun version1_firstBarWithSlices_becomesSharedList() {
        val v1 = """
            {"schemaVersion": 1, "bars": [
              {"id": "left", "edge": "LEFT", "slices": []},
              {"id": "right", "edge": "RIGHT", "slices": [
                {"primaryPackage": "org.example.mail", "secondaryPackage": "org.example.chat"},
                {"primaryPackage": "org.example.maps"}
              ]}
            ]}
        """.trimIndent()
        val migrated = ConfigCodec.decode(v1)!!
        assertEquals(FanletConfig.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(
            listOf(SliceConfig("org.example.mail", "org.example.chat"), SliceConfig("org.example.maps")),
            migrated.slices,
        )
        assertEquals(listOf("left", "right"), migrated.bars.map { it.id })
    }

    @Test
    fun version1_bothBarsWithSlices_leftBarWins() {
        val v1 = """
            {"schemaVersion": 1, "bars": [
              {"id": "left", "edge": "LEFT", "slices": [{"primaryPackage": "a"}]},
              {"id": "right", "edge": "RIGHT", "slices": [{"primaryPackage": "b"}]}
            ]}
        """.trimIndent()
        assertEquals(listOf(SliceConfig("a")), ConfigCodec.decode(v1)!!.slices)
    }

    @Test
    fun version1_withoutSlices_givesEmptyList() {
        assertEquals(emptyList<SliceConfig>(), ConfigCodec.decode("""{"schemaVersion": 1}""")!!.slices)
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
