package dev.morse.core.layout

import dev.morse.core.apps.AppKey
import dev.morse.core.apps.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutCodecTest {

    private var n = 0
    private val ids: () -> String = { "i${n++}" }

    private fun app(name: String) = AppKey("pkg.$name", "pkg.$name.Main", 0)

    @Test
    fun roundTripsTheDefaultLayout() {
        val roles = Role.entries.associateWith { app(it.name.lowercase()) }
        val layout = DefaultLayout.build(roles, listOf(app("x"), app("y")), ids)
        val decoded = LayoutCodec.decode(LayoutCodec.encode(layout), ids)
        assertEquals(layout, decoded)
    }

    @Test
    fun roundTripsHostedWidgetsAndWorkProfileApps() {
        val work = AppKey("com.mail", "com.mail.Main", 10)
        val layout = HomeLayout(
            columns = 5,
            pages = listOf(
                HomePage(
                    "p",
                    listOf(
                        WidgetItem("h", Cell(0, 0), WidgetKind.HOSTED, GridSpan(3, 1), hostedId = 7, provider = "com.x/.Provider"),
                        AppItem("w", Cell(4, 0), work),
                    ),
                ),
            ),
            dock = listOf(null, work, null, null),
        )
        assertEquals(layout, LayoutCodec.decode(LayoutCodec.encode(layout), ids))
    }

    @Test
    fun garbageIsNotALayout() {
        assertNull(LayoutCodec.decode("not json", ids))
        assertNull(LayoutCodec.decode("{}", ids))
        assertNull(LayoutCodec.decode("[1,2]", ids))
    }

    @Test
    fun unknownEntriesAreSkipped() {
        val text = """
            {"version":1,"columns":4,"rows":6,"dock":["a/b#0"],
             "pages":[{"id":"p","items":[
               {"type":"widget","kind":"FUTURE_WIDGET","w":2,"h":2,"c":0,"r":0},
               {"type":"widget","kind":"HOSTED","w":2,"h":2,"c":0,"r":0},
               {"type":"rocket","c":1,"r":1},
               {"type":"app","id":"ok","app":"a/b#0","c":3,"r":5},
               {"type":"folder","id":"f","apps":["c/d#0"],"c":0,"r":0}
             ]}]}
        """.trimIndent()
        val layout = LayoutCodec.decode(text, ids)!!
        val items = layout.pages.single().items
        assertEquals(2, items.size)
        // A one-app folder is read back as that app.
        assertTrue(items.all { it is AppItem })
        assertEquals(HomeLayout.DOCK_SIZE, layout.dock.size)
        assertEquals(AppKey("a", "b", 0), layout.dock[0])
    }

    @Test
    fun outOfRangeGridSizesAreClamped() {
        val text = """{"columns":9,"rows":99,"pages":[{"id":"p","items":[]}]}"""
        val layout = LayoutCodec.decode(text, ids)!!
        assertEquals(5, layout.columns)
        assertEquals(8, layout.rows)
    }
}
