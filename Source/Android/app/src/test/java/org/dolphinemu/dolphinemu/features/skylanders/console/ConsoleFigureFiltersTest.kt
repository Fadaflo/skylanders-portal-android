package org.dolphinemu.dolphinemu.features.skylanders.console

import org.dolphinemu.dolphinemu.features.skylanders.catalog.CatalogFigure
import org.junit.Assert.*
import org.junit.Test

class ConsoleFigureFiltersTest {
    private val hero = ConsoleFigure("hero.sky", "Gill Grunt", "Water", null, false, "Giants", "Skylander")
    private val repaint = hero.copy(path = "repaint.sky", name = "Legendary Gill Grunt", specialVariant = true)
    private val trap = hero.copy(path = "trap.sky", name = "Water Trap", game = "TrapTeam", type = "Trap")
    private val maxed = hero.copy(path = "maxed.sky", ultimate = true)
    private val fire = hero.copy(path = "fire.sky", name = "Eruptor", element = "Fire", game = "SpyrosAdv")
    private val collection = listOf(trap, repaint, maxed, fire, hero)

    @Test fun combinedFiltersDoNotMixCollectionOrElements() {
        val result = filterConsoleFigures(collection, false, "  gill ", "Giants", "Water", false, false, "name")
        assertEquals(listOf(hero), result)
        assertEquals(listOf(maxed), filterConsoleFigures(collection, true, "", "", "", true, true, "game"))
    }

    @Test fun trapsAndVariantsCanBeHiddenIndependently() {
        val withoutTraps = filterConsoleFigures(collection, false, "", "", "", true, false, "game")
        assertTrue(repaint in withoutTraps)
        assertFalse(trap in withoutTraps)
        val withoutVariants = filterConsoleFigures(collection, false, "", "", "", false, true, "element")
        assertTrue(trap in withoutVariants)
        assertFalse(repaint in withoutVariants)
    }

    @Test fun gameSortingUsesReleaseOrderInsteadOfAlphabeticalOrder() {
        val result = filterConsoleFigures(listOf(trap, hero, fire), false, "", "", "", true, true, "game")
        assertEquals(listOf(fire, hero, trap), result)
    }

    @Test fun variantDetectionKeepsBaseSeriesTwoAndUniqueSpecials() {
        val base = CatalogFigure(1, 0x1000, "Base", "Giants", "Water", "Skylander", null)
        assertFalse(collectionFigure("base.sky", base, false, setOf(1)).specialVariant)
        assertFalse(collectionFigure("s2.sky", base.copy(variant = 0x1801), false, setOf(1)).specialVariant)
        assertTrue(collectionFigure("special.sky", base.copy(variant = 0x1803), false, setOf(1)).specialVariant)
        assertFalse(collectionFigure("unique.sky", base.copy(id = 2, variant = 0x1803), false, setOf(1)).specialVariant)
    }
}
