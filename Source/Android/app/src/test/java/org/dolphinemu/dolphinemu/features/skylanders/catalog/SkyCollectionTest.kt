// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders.catalog

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SkyCollectionTest {
    @get:Rule val temp = TemporaryFolder()
    private val item = CatalogFigure(202, 0, "Healing Elixir", "SpyrosAdv", "Other", "Item", null)

    private fun file(name: String, id: Int = 202, variant: Int = 0): File {
        val dir = SkyCollection.folder(temp.root.path).apply { mkdirs() }
        val bytes = ByteArray(1024)
        bytes[0x10] = id.toByte()
        bytes[0x11] = (id shr 8).toByte()
        bytes[0x1c] = variant.toByte()
        bytes[0x1d] = (variant shr 8).toByte()
        return File(dir, name).apply { writeBytes(bytes) }
    }

    @Test fun freshItemReplacesAllOlderCopiesByIdentity() {
        val old = file("Healing Elixir.sky")
        val renamed = file("My potion (9).sky")
        val fresh = file("Healing Elixir (10).sky")
        val otherItem = file("Empire of Ice.sky", 301)
        val otherVariant = file("Special potion.sky", variant = 0x4000)
        val hero = file("Bash.sky", 4)
        val heroBytes = hero.readBytes()
        assertTrue(SkyCollection.pruneItemCopies(temp.root.path, item, fresh).isEmpty())
        assertFalse(old.exists())
        assertFalse(renamed.exists())
        assertTrue(fresh.exists())
        assertTrue(otherItem.exists())
        assertTrue(otherVariant.exists())
        assertArrayEquals(heroBytes, hero.readBytes())
    }

    @Test fun failedCreationPreservesExistingItem() {
        val old = file("Healing Elixir.sky")
        val incomplete = file("Healing Elixir (2).sky").apply { writeBytes(byteArrayOf(0)) }
        SkyCollection.pruneItemCopies(temp.root.path, item, incomplete)
        assertTrue(old.exists())
    }

    @Test fun unreleasedPortalFileIsRetained() {
        val old = file("Healing Elixir.sky")
        val fresh = file("Healing Elixir (2).sky")
        val failed = SkyCollection.pruneItemCopies(temp.root.path, item, fresh) { false }
        assertEquals(listOf(old), failed)
        assertTrue(old.exists())
        assertTrue(fresh.exists())
    }

    @Test fun heroesTrapsVehiclesAndTrophiesAreNeverPruned() {
        for (type in listOf("Skylander", "Giant", "Swapper", "Trap", "Vehicle", "Trophy")) {
            val old = file("$type.sky")
            val fresh = file("$type (2).sky")
            SkyCollection.pruneItemCopies(temp.root.path, item.copy(type = type), fresh) {
                fail("Must not release progress figures")
                false
            }
            assertTrue(old.exists())
            assertTrue(fresh.exists())
        }
    }

    @Test fun adventureItemsAreReplacedAndUltimateFolderIsUntouched() {
        val old = file("Empire of Ice.sky", 301)
        val fresh = file("Empire of Ice (2).sky", 301)
        val ultimate = File(SkyCollection.maxedFolder(temp.root.path).apply { mkdirs() }, "Empire of Ice.sky")
            .apply { writeBytes(old.readBytes()) }
        SkyCollection.pruneItemCopies(temp.root.path, item.copy(id = 301), fresh)
        assertFalse(old.exists())
        assertTrue(fresh.exists())
        assertTrue(ultimate.exists())
    }
}
