package com.cocode.measureapp.ui

import com.cocode.measureapp.R
import com.cocode.measureapp.model.TextKey
import com.cocode.measureapp.ui.text.stringRes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words for every message key exist, one string each, and none is left over. */
class UiTextResourcesTest {
    private val messageNames: Set<String> by lazy {
        val dir = File("src/main/res/values")
        val names = Regex("""<string name="(msg_[a-z0-9_]+)"""")
        dir.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }!!
            .flatMap { names.findAll(it.readText()).map { m -> m.groupValues[1] }.toList() }
            .toSet()
    }

    @Test fun everyKeyHasItsOwnStringResource() {
        val ids = TextKey.entries.map { it.stringRes() }
        assertEquals("two keys share a string", ids.size, ids.toSet().size)
    }

    @Test fun everyKeyIsMappedToItsOwnMsgString() {
        for (key in TextKey.entries) {
            val expected = R.string::class.java.getField("msg_" + key.name.lowercase()).getInt(null)
            assertEquals("$key maps to the wrong string", expected, key.stringRes())
        }
    }

    @Test fun everyKeyIsWordedInStringsXml() {
        val missing = TextKey.entries.map { "msg_" + it.name.lowercase() }.filter { it !in messageNames }
        assertTrue("no string for $missing", missing.isEmpty())
    }

    @Test fun noMessageStringIsLeftWithoutAKey() {
        val keys = TextKey.entries.map { "msg_" + it.name.lowercase() }.toSet()
        assertTrue("no key for ${messageNames - keys}", (messageNames - keys).isEmpty())
    }
}
