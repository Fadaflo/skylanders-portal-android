// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.catalog

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import org.dolphinemu.dolphinemu.databinding.ActivitySkylanderPortalBinding
import org.dolphinemu.dolphinemu.databinding.ListItemPortalSlotBinding
import org.dolphinemu.dolphinemu.features.skylanders.SkylanderConfig
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization

/**
 * Das Skylanders-Menue: Portalplaetze, Sammlung und Figurenraster in einer
 * Vollbildansicht, bedienbar mit dem Gamepad.
 *
 * Vier Plaetze statt der sechzehn, die das Portal koennte — fuer Giants zu
 * zweit mit Gegenstaenden reicht das. SLOT_COUNT erhoehen, wenn Swap Force
 * dazukommt.
 */
class SkylanderPortalActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySkylanderPortalBinding
    private lateinit var slotViews: List<ListItemPortalSlotBinding>
    private lateinit var adapter: SkylanderFigureAdapter

    private var allFigures: List<CatalogFigure> = emptyList()
    private var owned: Map<Pair<Int, Int>, CollectedFigure> = emptyMap()

    private var elementFilter = 0
    private var gameFilter = 0
    private var onlyCollection = false
    private var activeSlot = 0

    private var lastStickMove = 0L
    private var columns = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySkylanderPortalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        allFigures = SkylanderCatalog.getFigures(this)
        rescanCollection()
        buildSlotRow()

        adapter = SkylanderFigureAdapter(
            figures = visibleFigures(),
            isOwned = { owned.containsKey(it.id to it.variant) },
            onClick = { place(it) }
        )
        columns = columnCount()
        binding.gridFigures.layoutManager = GridLayoutManager(this, columns)
        binding.gridFigures.adapter = adapter
        adapter.selected = 0

        binding.textFilterElement.setOnClickListener { cycleElement() }
        binding.textFilterGame.setOnClickListener { cycleGame() }
        binding.textFilterCollection.setOnClickListener { toggleCollection() }

        binding.textHelp.text = HELP_LINE

        updateFilterLabels()
        updateStatus()
    }

    /** Spaltenzahl nach Bildschirmbreite; quer werden es mehr als hochkant. */
    private fun columnCount(): Int =
        (resources.configuration.screenWidthDp / COLUMN_WIDTH_DP).coerceIn(3, 10)

    // ------------------------------------------------------------------ Daten

    /**
     * Dolphins Nutzerverzeichnis. Ueber das Spielmenue ist es laengst
     * eingerichtet; beim kalten Start dieser Activity (etwa per adb) noch
     * nicht, deshalb der Rueckfallweg ueber den Pfad.
     */
    private fun userDirectory(): String = try {
        DirectoryInitialization.getUserDirectory()
    } catch (e: IllegalStateException) {
        DirectoryInitialization.getUserDirectoryPath(this)?.absolutePath
            ?: (getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath)
    }

    private fun rescanCollection() {
        owned = SkyCollection.scan(userDirectory()).associateBy { it.id to it.variant }
    }

    private fun visibleFigures(): List<CatalogFigure> = allFigures.filter { f ->
        (elementFilter == 0 || f.element == ELEMENTS[elementFilter]) &&
            (gameFilter == 0 || f.game == GAMES[gameFilter]) &&
            (!onlyCollection || owned.containsKey(f.id to f.variant))
    }

    // ------------------------------------------------------------ Portalplaetze

    private fun buildSlotRow() {
        val inflater = LayoutInflater.from(this)
        slotViews = (0 until SLOT_COUNT).map { index ->
            val slotBinding = ListItemPortalSlotBinding.inflate(inflater, binding.rowSlots, true)
            slotBinding.root.setOnClickListener {
                activeSlot = index
                updateSlots()
                updateStatus()
            }
            slotBinding
        }
        updateSlots()
    }

    private fun updateSlots() {
        for (index in 0 until SLOT_COUNT) {
            val view = slotViews[index]
            val slot = slots[index]
            val marker = if (index == activeSlot) ACTIVE_MARKER else ""
            view.textSlotNumber.text = marker + "Platz " + (index + 1)
            view.textSlotLabel.text = slot.figure?.name ?: "leer"
            view.imageSlotFigure.setImageBitmap(
                slot.figure?.let { SkylanderCatalog.getArtwork(this, it) }
            )
            view.root.alpha = if (index == activeSlot) 1.0f else 0.6f
        }
    }

    private fun place(figure: CatalogFigure) {
        val slot = slots[activeSlot]
        val existing = owned[figure.id to figure.variant]

        val result = if (existing != null) {
            SkylanderConfig.loadSkylander(slot.portalSlot, existing.file.absolutePath)
        } else {
            val file = SkyCollection.newFileFor(userDirectory(), figure)
            file.parentFile?.mkdirs()
            val created = SkylanderConfig.createSkylander(
                figure.id,
                figure.variant,
                file.absolutePath,
                slot.portalSlot
            )
            rescanCollection()
            adapter.submit(visibleFigures())
            created
        }

        val portalSlot = result?.first
        if (portalSlot == null) {
            binding.textStatus.text = figure.name + " liess sich nicht auflegen"
            return
        }

        slot.portalSlot = portalSlot
        slot.figure = figure
        updateSlots()
        updateStatus()
    }

    private fun clearActiveSlot() {
        val slot = slots[activeSlot]
        if (slot.portalSlot >= 0) {
            SkylanderConfig.removeSkylander(slot.portalSlot)
        }
        slot.portalSlot = -1
        slot.figure = null
        updateSlots()
        updateStatus()
    }

    // ----------------------------------------------------------------- Filter

    private fun cycleElement() {
        elementFilter = (elementFilter + 1) % ELEMENTS.size
        refresh()
    }

    private fun cycleGame() {
        gameFilter = (gameFilter + 1) % GAMES.size
        refresh()
    }

    private fun toggleCollection() {
        onlyCollection = !onlyCollection
        refresh()
    }

    private fun refresh() {
        updateFilterLabels()
        adapter.submit(visibleFigures())
        adapter.selected = if (adapter.itemCount > 0) 0 else -1
        binding.gridFigures.scrollToPosition(0)
        updateStatus()
    }

    private fun updateFilterLabels() {
        binding.textFilterElement.text =
            "Element: " + if (elementFilter == 0) "alle" else ELEMENTS[elementFilter]

        val game = GAMES[gameFilter]
        binding.textFilterGame.text =
            "Spiel: " + if (gameFilter == 0) "alle"
            else (CatalogFigure.GAME_LABELS[game] ?: game)

        binding.textFilterCollection.text =
            "Sammlung: " + if (onlyCollection) "nur eigene" else "alle"
    }

    private fun updateStatus() {
        val marked = adapter.figureAt(adapter.selected)
        binding.textStatus.text = if (marked != null) {
            marked.name + "  (" + adapter.itemCount + " Figuren, " + owned.size + " eigene)"
        } else {
            adapter.itemCount.toString() + " Figuren, " + owned.size + " in der Sammlung"
        }
    }

    // ---------------------------------------------------------------- Auswahl

    /**
     * Bewegt die Markierung im Raster. Eigene Rechnung statt Androids
     * Fokussuche, damit das Steuerkreuz auch als Achse funktioniert.
     */
    private fun moveSelection(dx: Int, dy: Int) {
        val count = adapter.itemCount
        if (count == 0) return

        val current = adapter.selected.coerceAtLeast(0)
        var next = current + dx + dy * columns
        if (dx != 0) {
            // Zeilenwechsel am Rand vermeiden gibt es bewusst nicht: durchlaufen
            // ist beim Blaettern durch 517 Figuren angenehmer.
            next = next.coerceIn(0, count - 1)
        } else {
            if (next < 0 || next >= count) return
        }

        adapter.selected = next
        binding.gridFigures.scrollToPosition(next)
        updateStatus()
    }

    private fun placeSelected() {
        adapter.figureAt(adapter.selected)?.let { place(it) }
    }

    // ------------------------------------------------------------- Controller

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> {
                placeSelected()
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveSelection(-1, 0)
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveSelection(1, 0)
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                moveSelection(0, -1)
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveSelection(0, 1)
                return true
            }

            KeyEvent.KEYCODE_BUTTON_B -> {
                finish()
                return true
            }

            KeyEvent.KEYCODE_BUTTON_X -> {
                clearActiveSlot()
                return true
            }

            KeyEvent.KEYCODE_BUTTON_Y -> {
                toggleCollection()
                return true
            }

            KeyEvent.KEYCODE_BUTTON_L1 -> {
                activeSlot = (activeSlot + SLOT_COUNT - 1) % SLOT_COUNT
                updateSlots()
                return true
            }

            KeyEvent.KEYCODE_BUTTON_R1 -> {
                activeSlot = (activeSlot + 1) % SLOT_COUNT
                updateSlots()
                return true
            }

            KeyEvent.KEYCODE_BUTTON_L2 -> {
                cycleElement()
                return true
            }

            KeyEvent.KEYCODE_BUTTON_R2 -> {
                cycleGame()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Der linke Stick bewegt die Auswahl. Aus dem Steuerkreuz macht Android von
     * sich aus Navigationsereignisse, aus dem Stick nicht.
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == 0) {
            return super.onGenericMotionEvent(event)
        }

        val now = System.currentTimeMillis()
        if (now - lastStickMove < STICK_REPEAT_MS) return true

        // Linker Stick und Steuerkreuz. Das Steuerkreuz des DualShock 4 kommt
        // als Achsenpaar HAT_X/HAT_Y, nicht als Taste.
        val x = event.getAxisValue(MotionEvent.AXIS_X) + event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y) + event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        val dx = if (x < -STICK_THRESHOLD) -1 else if (x > STICK_THRESHOLD) 1 else 0
        val dy = if (y < -STICK_THRESHOLD) -1 else if (y > STICK_THRESHOLD) 1 else 0
        if (dx == 0 && dy == 0) return true

        lastStickMove = now
        if (dy != 0) moveSelection(0, dy) else moveSelection(dx, 0)
        return true
    }

    /** Ein Portalplatz im Menue. */
    private class Slot {
        var portalSlot: Int = -1
        var figure: CatalogFigure? = null
    }

    companion object {
        /** Plaetze, die das Menue zeigt. Das Portal selbst koennte sechzehn. */
        const val SLOT_COUNT = 4

        private const val COLUMN_WIDTH_DP = 110
        private const val STICK_THRESHOLD = 0.6f
        private const val STICK_REPEAT_MS = 160L
        private const val ACTIVE_MARKER = "> "

        private const val HELP_LINE =
            "Kreuz auflegen  |  Kreis zurueck  |  Quadrat Platz leeren  |  " +
                "Dreieck nur Sammlung  |  L1/R1 Platz  |  L2 Element  |  R2 Spiel"

        private val ELEMENTS = listOf(
            "", "Magic", "Fire", "Air", "Life", "Undead", "Earth", "Water", "Tech",
            "Dark", "Light", "Other"
        )

        private val GAMES = listOf(
            "", "SpyrosAdv", "Giants", "SwapForce", "TrapTeam", "Superchargers"
        )

        /**
         * Der Portalzustand lebt im C++-Kern und ueberlebt diese Activity.
         * Deshalb wird die Zuordnung Platz zu Figur hier statisch gehalten.
         */
        private val slots = Array(SLOT_COUNT) { Slot() }
    }
}
