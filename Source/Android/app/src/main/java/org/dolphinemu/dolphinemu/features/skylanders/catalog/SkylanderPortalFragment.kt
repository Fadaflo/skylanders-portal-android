// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.catalog

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.databinding.FragmentSkylanderPortalBinding
import org.dolphinemu.dolphinemu.databinding.ItemSkyElementBinding
import org.dolphinemu.dolphinemu.databinding.ItemSkyHintBinding
import org.dolphinemu.dolphinemu.databinding.ListItemPortalSlotBinding
import org.dolphinemu.dolphinemu.features.skylanders.SkylanderConfig
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import java.io.File

/**
 * Das Skylanders-Menue als Schicht ueber dem laufenden Spiel, nachgebaut nach
 * dem RSX-Overlay des PS3-Projekts.
 *
 * Bewusst ein Fragment und kein eigenes Fenster: sobald Android ein Fenster
 * ueber die EmulationActivity legt, ruft es deren onPause, und
 * EmulationFragment haelt die Emulation an. Als Schicht im selben Fenster
 * laeuft das Spiel samt Ton weiter.
 *
 * Zwei Bereiche teilen sich den Bildschirm: oben die Portalplaetze, darunter
 * das Raster. Das Raster zeigt entweder die eigene Sammlung — eine Karte je
 * .sky-Datei, eine zweite Kopie einer Figur ist eine eigene Karte — oder alle
 * Figuren, die es gibt, zum Beschwoeren.
 */
class SkylanderPortalFragment : Fragment() {

    private var _binding: FragmentSkylanderPortalBinding? = null
    private val binding get() = _binding!!

    private lateinit var slotViews: List<ListItemPortalSlotBinding>
    private lateinit var elementViews: List<ItemSkyElementBinding>
    private lateinit var adapter: SkylanderFigureAdapter

    private var allFigures: List<CatalogFigure> = emptyList()
    private var byKey: Map<Pair<Int, Int>, CatalogFigure> = emptyMap()
    private var collection: List<CollectedFigure> = emptyList()
    private var rows: List<FigureRow> = emptyList()
    private var plainVariantExists: Set<Int> = emptySet()

    // Ansicht, Filter und Platz ueberleben das Schliessen des Menues: beim
    // naechsten Oeffnen steht alles so, wie man es verlassen hat.
    private var mode
        get() = savedMode
        set(value) { savedMode = value }
    private var elementFilter
        get() = savedElement
        set(value) { savedElement = value }
    private var gameFilter
        get() = savedGame
        set(value) { savedGame = value }
    private var activeSlot
        get() = savedSlot
        set(value) { savedSlot = value }
    private var upperPath
        get() = savedUpperPath
        set(value) { savedUpperPath = value }
    private var allVariants
        get() = savedAllVariants
        set(value) { savedAllVariants = value }

    private var focus = Focus.GRID
    var activePlayer = 1
        private set
    private var deleteMode = false
    private var maxedFiles: Map<Pair<Int, Int>, CollectedFigure> = emptyMap()
    private var confirming = false
    private val marked = HashSet<String>()

    private var lastStickMove = 0L
    private var columns = 4

    private val handler = Handler(Looper.getMainLooper())
    private var hiddenUntilKey = false
    private var hiddenTimed = false

    /** Wird von der EmulationActivity gesetzt, damit das Menue sich schliessen kann. */
    var onClose: (() -> Unit)? = null

    private enum class Mode { COLLECTION, SUMMON, MAXED, ITEMS }
    private enum class Focus { PORTAL, GRID }

    // ------------------------------------------------------------ Lebenszyklus

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSkylanderPortalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        allFigures = SkylanderCatalog.getFigures(requireContext())
        byKey = allFigures.associateBy { it.id to it.variant }
        plainVariantExists = allFigures
            .filter { (it.variant and 0xFF) < FIRST_SPECIAL }
            .map { it.id }
            .toHashSet()
        rescanCollection()

        buildSlotRow()
        buildElementBar()

        adapter = SkylanderFigureAdapter(emptyList()) { position ->
            adapter.selected = position
            focus = Focus.GRID
            accept()
        }
        columns = columnCount()
        binding.gridFigures.layoutManager = GridLayoutManager(requireContext(), columns)
        binding.gridFigures.adapter = adapter

        binding.textElementPrev.setOnClickListener { cycleElement(-1) }
        binding.textElementNext.setOnClickListener { cycleElement(1) }
        binding.textFilterGame.setOnClickListener { cycleGame() }
        binding.badgeGame.setOnClickListener { cycleGame() }
        binding.textFilterVariants.setOnClickListener { toggleVariants() }
        binding.badgeVariants.setOnClickListener { toggleVariants() }
        binding.textModeHint.setOnClickListener { toggleDeleteMode() }
        binding.tabCollection.setOnClickListener { switchTo(Mode.COLLECTION) }
        binding.tabSummon.setOnClickListener { switchTo(Mode.SUMMON) }
        binding.tabMaxed.setOnClickListener { switchTo(Mode.MAXED) }
        binding.tabItems.setOnClickListener { switchTo(Mode.ITEMS) }

        rebuildRows()
        restoreCursor()
        selectPlayer(activePlayer)
    }

    override fun onDestroyView() {
        savedCursors[mode.ordinal] = adapter.selected
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _binding = null
    }

    /** Spaltenzahl nach Bildschirmbreite; quer werden es mehr als hochkant. */
    private fun columnCount(): Int =
        (resources.configuration.screenWidthDp / COLUMN_WIDTH_DP).coerceIn(3, 10)

    // ------------------------------------------------------------------ Daten

    /**
     * Dolphins Nutzerverzeichnis. Aus dem Spielmenue heraus ist es laengst
     * eingerichtet; der Rueckfallweg deckt den kalten Start ab.
     */
    private fun userDirectory(): String = try {
        DirectoryInitialization.getUserDirectory()
    } catch (e: IllegalStateException) {
        DirectoryInitialization.getUserDirectoryPath(requireContext())?.absolutePath
            ?: requireContext().filesDir.absolutePath
    }

    private fun rescanCollection() {
        maxedFiles = SkyCollection.scanMaxed(userDirectory(), upperPath)
            .associateBy { it.id to it.variant }
        collection = SkyCollection.scan(userDirectory()).sortedWith(
            compareBy(
                { elementOrder(byKey[it.id to it.variant]?.element) },
                { byKey[it.id to it.variant]?.name ?: "" },
                { it.file.name }
            )
        )
    }

    /**
     * Sortierschluessel: erst nach Element in der Reihenfolge der Filterleiste,
     * dann nach Name. So steht bei „Element: alle" trotzdem Magie vor Feuer vor
     * Luft, statt in der Reihenfolge der Datenbank.
     */
    private fun sortKey(figure: CatalogFigure): String =
        elementOrder(figure.element).toString().padStart(2, '0') + " " + figure.name

    private fun elementOrder(element: String?): Int =
        ELEMENTS.indexOf(element ?: "").let { if (it < 0) ELEMENTS.size else it }

    private fun passesFilter(figure: CatalogFigure): Boolean =
        (elementFilter == 0 || figure.element == ELEMENTS[elementFilter]) &&
            (gameFilter == 0 || figure.game == GAMES[gameFilter]) &&
            (allVariants || !isRepaint(figure))

    /**
     * Umlackierungen erkennen — an der Variantennummer, nicht am Namen.
     *
     * Das niedrige Byte der Variante zaehlt die Ausgabe durch: 0x00 die
     * Grundfigur, 0x01 Series 2, 0x02 Series 3, 0x03 Legendary, 0x06 LightCore,
     * ab 0x10 die Umlackierungen.
     *
     * „Standard" zeigt nur 0x00 und 0x01 — die Figur, wie sie im Spiel
     * normalerweise vorkommt. Alles andere, auch LightCore, Legendary und Gold,
     * steht unter „alle".
     *
     * Nur ausblenden, wenn es dieselbe Figur auch als Grundausgabe gibt — sonst
     * verschwaenden Figuren ganz, die es nur als Sonderfassung gibt.
     */
    private fun isRepaint(figure: CatalogFigure): Boolean =
        (figure.variant and 0xFF) >= FIRST_SPECIAL && plainVariantExists.contains(figure.id)

    private fun slotOfFile(path: String): Int =
        slots.indexOfFirst { it.filePath == path }

    /** Baut die Karten des Rasters neu auf, passend zur Ansicht und den Filtern. */
    private fun rebuildRows(resetCursor: Boolean = false) {
        val ownedKeys = collection.map { it.id to it.variant }.toHashSet()

        rows = when (mode) {
            Mode.COLLECTION -> collection.mapNotNull { file ->
                val figure = byKey[file.id to file.variant] ?: return@mapNotNull null
                if (!passesFilter(figure)) return@mapNotNull null

                val path = file.file.absolutePath
                val slot = slotOfFile(path)
                FigureRow(
                    figure = figure,
                    file = file,
                    owned = true,
                    state = when {
                        marked.contains(path) -> "zum Löschen vorgemerkt"
                        slot >= 0 -> "auf Platz " + (slot + 1)
                        else -> null
                    },
                    marked = marked.contains(path)
                )
            }

            Mode.SUMMON -> allFigures.filter { passesFilter(it) }.sortedBy(::sortKey).map { figure ->
                FigureRow(
                    figure = figure,
                    file = null,
                    owned = ownedKeys.contains(figure.id to figure.variant),
                    state = null
                )
            }

            Mode.ITEMS -> allFigures
                .filter { passesFilter(it) && it.type in ITEM_TYPES }
                .sortedBy { it.gameLabel + " " + it.name }
                .map { figure ->
                    FigureRow(
                        figure = figure,
                        file = null,
                        owned = ownedKeys.contains(figure.id to figure.variant),
                        state = ITEM_LABELS[figure.type]
                    )
                }

            Mode.MAXED -> allFigures
                .filter { passesFilter(it) && it.type in MAXABLE_TYPES }
                .sortedBy(::sortKey)
                .map { figure ->
                    val fertig = maxedFiles.containsKey(figure.id to figure.variant)
                    FigureRow(
                        figure = figure,
                        file = maxedFiles[figure.id to figure.variant],
                        owned = fertig,
                        state = if (fertig) "ultimativ vorhanden" else null
                    )
                }
        }

        adapter.submit(rows)
        if (resetCursor || adapter.selected >= rows.size) {
            adapter.selected = if (rows.isEmpty()) -1 else 0
            binding.gridFigures.scrollToPosition(0)
        }

        updateTitle()
        updateSlots()
        updateElementBar()
        updateButtonBar()
    }

    // ------------------------------------------------------------ Portalplätze

    private fun buildSlotRow() {
        val inflater = LayoutInflater.from(requireContext())
        slotViews = (0 until SLOT_COUNT).map { index ->
            val slotBinding = ListItemPortalSlotBinding.inflate(inflater, binding.rowSlots, true)
            slotBinding.root.setOnClickListener {
                focus = Focus.PORTAL
                activeSlot = index
                updateSlots()
                updateButtonBar()
            }
            slotBinding
        }
    }

    /** A controller opens the grid directly for its own figure. */
    fun selectPlayer(player: Int) {
        activePlayer = player.coerceIn(1, 2)
        activeSlot = activePlayer - 1
        focus = Focus.GRID
        if (_binding != null) {
            updateSlots()
            updateButtonBar()
            setStatus("Spieler $activePlayer · Figur auswählen und mit X auf das Portal stellen.")
        }
    }

    private fun updateSlots() {
        for (index in 0 until SLOT_COUNT) {
            val view = slotViews[index]
            val slot = slots[index]
            val here = focus == Focus.PORTAL && index == activeSlot

            val chosen = index == activeSlot
            view.textSlotNumber.text = (if (here) "▶ " else "") + when (index) {
                0 -> "SPIELER 1"
                1 -> "SPIELER 2"
                else -> "EXTRA " + (index - 1)
            }
            view.textSlotLabel.text = slot.figure?.name ?: "leer"
            view.imageSlotFigure.setImageBitmap(
                slot.figure?.let { SkylanderCatalog.getArtwork(requireContext(), it) }
            )
            // Der gewaehlte Platz bleibt umrandet, auch wenn das Raster am Zug
            // ist: er ist eine bleibende Auswahl, kein Fokus auf Zeit.
            view.root.setBackgroundResource(
                if (chosen) R.drawable.sky_slot_frame_focused else R.drawable.sky_slot_frame
            )
            view.root.alpha = if (chosen) 1.0f else 0.6f
        }
        adapter.selectionVisible = focus == Focus.GRID
    }

    // ------------------------------------------------------------- Elementleiste

    private fun buildElementBar() {
        val inflater = LayoutInflater.from(requireContext())
        elementViews = ELEMENTS.mapIndexed { index, element ->
            val item = ItemSkyElementBinding.inflate(inflater, binding.rowElements, true)
            item.textElement.text = if (index == 0) "Alle" else GERMAN_ELEMENTS[element] ?: element
            item.imageElement.setImageBitmap(
                if (index == 0) null else SkylanderCatalog.getElementIcon(requireContext(), element)
            )
            item.imageElement.visibility = if (index == 0) View.GONE else View.VISIBLE
            item.root.setOnClickListener {
                elementFilter = index
                rebuildRows(resetCursor = true)
            }
            item
        }
    }

    private fun updateElementBar() {
        elementViews.forEachIndexed { index, item ->
            val active = index == elementFilter
            item.root.setBackgroundResource(if (active) R.drawable.sky_element_active else 0)
            item.root.alpha = if (active) 1.0f else 0.5f
        }

        // Das gewaehlte Element in die Mitte schieben, sonst verschwindet es
        // beim Durchschalten hinter dem rechten Rand.
        val chosen = elementViews.getOrNull(elementFilter)?.root
        if (chosen != null) {
            binding.scrollElements.post {
                val scroll = _binding?.scrollElements ?: return@post
                val target = chosen.left - (scroll.width - chosen.width) / 2
                scroll.smoothScrollTo(target.coerceAtLeast(0), 0)
            }
        }
        binding.textFilterGame.text = "Spiel: " +
            if (gameFilter == 0) "Alle"
            else (CatalogFigure.GAME_LABELS[GAMES[gameFilter]] ?: GAMES[gameFilter])
        binding.textFilterVariants.text =
            "Varianten: " + if (allVariants) "alle" else "Standard"
    }

    // -------------------------------------------------------------- Knopfleiste

    private fun updateButtonBar() {
        val bar = binding.rowButtons
        bar.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        fun hint(glyph: Int?, shoulder: String?, text: String) {
            val item = ItemSkyHintBinding.inflate(inflater, bar, true)
            if (glyph != null) {
                item.imageGlyph.setImageResource(glyph)
            } else {
                item.imageGlyph.visibility = View.GONE
            }
            if (shoulder != null) {
                item.textShoulder.text = shoulder
            } else {
                item.textShoulder.visibility = View.GONE
            }
            item.textHint.text = text
        }

        when {
            confirming -> {
                hint(R.drawable.ic_ps_cross, null, "Endgültig löschen")
                hint(R.drawable.ic_ps_circle, null, "Abbrechen")
            }

            deleteMode -> {
                hint(R.drawable.ic_ps_cross, null, "Vormerken")
                hint(R.drawable.ic_ps_square, null, "Löschen (" + marked.size + ")")
                hint(null, "Options", "Löschmodus beenden")
                hint(R.drawable.ic_ps_circle, null, "Zurück")
            }

            focus == Focus.PORTAL -> {
                hint(R.drawable.ic_ps_cross, null, "Platz leeren")
                hint(R.drawable.ic_ps_square, null, "Portal leeren")
                hint(null, "L2", "Menü ausblenden")
                hint(R.drawable.ic_ps_circle, null, "Zurück")
            }

            else -> {
                // Dreieck, Quadrat und Options stehen oben bei dem, was sie
                // bedienen. Hier steht nur noch, was sonst nirgends steht.
                hint(
                    R.drawable.ic_ps_cross, null,
                    when (mode) {
                        Mode.COLLECTION -> "Für "
                        Mode.SUMMON -> "Beschwören für "
                        Mode.MAXED -> "Ultimativ für "
                        Mode.ITEMS -> "Gegenstand für "
                    } + when (activeSlot) {
                        0 -> "Spieler 1"
                        1 -> "Spieler 2"
                        else -> "Extra " + (activeSlot - 1)
                    }
                )
                hint(null, "L2", "Menü ausblenden")
                hint(R.drawable.ic_ps_circle, null, "Zurück")
            }
        }

        binding.textModeHint.text = when {
            confirming -> "Wirklich löschen?"
            deleteMode -> "Options: Löschmodus beenden"
            mode == Mode.COLLECTION -> "Options: Löschmodus"
            mode == Mode.MAXED ->
                "Options: Pfad " + (if (upperPath) "oben" else "unten")
            mode == Mode.ITEMS -> "R2 filtert nach Spiel"
            else -> "Beschwören erschafft neue Figuren"
        }
    }

    /**
     * Die Reiter oben zeigen, in welcher Ansicht man steht. „Ultimative" ist
     * angelegt, aber noch blass — die fertig ausgebauten Figuren kommen in
     * Phase 7 dazu.
     */
    private fun updateTitle() {
        val total = when (mode) {
            Mode.COLLECTION -> collection.size
            Mode.ITEMS -> allFigures.count { it.type in ITEM_TYPES }
            else -> allFigures.size
        }
        binding.textTitle.text = rows.size.toString() + " von " + total +
            if (mode == Mode.MAXED) {
                "   ·   " + (if (upperPath) "oberer Pfad" else "unterer Pfad")
            } else {
                ""
            }

        fun tab(view: android.widget.TextView, active: Boolean, available: Boolean) {
            view.setBackgroundResource(if (active) R.drawable.sky_element_active else 0)
            view.alpha = when {
                active -> 1.0f
                available -> 0.55f
                else -> 0.3f
            }
        }

        tab(binding.tabCollection, mode == Mode.COLLECTION, true)
        tab(binding.tabSummon, mode == Mode.SUMMON, true)
        tab(binding.tabMaxed, mode == Mode.MAXED, true)
        tab(binding.tabItems, mode == Mode.ITEMS, true)
    }

    private fun setStatus(text: String) {
        _binding?.textStatus?.text = text
    }

    // ------------------------------------------------------------------ Aktionen

    private fun accept() {
        if (focus == Focus.PORTAL) {
            ejectSlot(activeSlot)
            return
        }

        val row = adapter.rowAt(adapter.selected) ?: return

        if (deleteMode) {
            val path = row.file?.file?.absolutePath ?: return
            if (!marked.remove(path)) marked.add(path)
            rebuildRows()
            setStatus(marked.size.toString() + " zum Löschen vorgemerkt.")
            return
        }

        when (mode) {
            Mode.COLLECTION -> row.file?.let { placeFile(row.figure, it) }
            Mode.SUMMON -> summon(row.figure)
            Mode.MAXED -> placeMaxed(row.figure)
            Mode.ITEMS -> summon(row.figure)
        }
    }

    /**
     * Legt eine Figur auf den gewaehlten Platz. Liegt dort schon eine, wird sie
     * ersetzt: Dolphin nimmt die alte herunter — ihr Spielstand wird dabei
     * ganz normal geschrieben — und legt die neue auf denselben Platz.
     */
    private fun placeFile(figure: CatalogFigure, file: CollectedFigure) {
        val target = activeSlot
        val slot = slots[target]
        val vorher = slot.figure?.name

        val result = SkylanderConfig.loadSkylander(slot.portalSlot, file.file.absolutePath)
        val portalSlot = result?.first
        if (portalSlot == null) {
            setStatus(figure.name + " ließ sich nicht auflegen.")
            return
        }

        slot.portalSlot = portalSlot
        slot.figure = figure
        slot.filePath = file.file.absolutePath
        activeSlot = target

        rebuildRows()
        onClose?.invoke()
    }

    private fun summon(figure: CatalogFigure) {
        val target = activeSlot
        val slot = slots[target]

        val file = SkyCollection.newFileFor(userDirectory(), figure)
        file.parentFile?.mkdirs()

        val created = SkylanderConfig.createSkylander(
            figure.id,
            figure.variant,
            file.absolutePath,
            slot.portalSlot
        )

        val portalSlot = created?.first
        if (portalSlot == null) {
            setStatus(figure.name + " ließ sich nicht erschaffen.")
            return
        }

        slot.portalSlot = portalSlot
        slot.figure = figure
        slot.filePath = file.absolutePath
        activeSlot = target

        SkyCollection.pruneItemCopies(userDirectory(), figure, file) { oldFile ->
            // The native portal saves and closes its file synchronously on removal.
            // Never unlink a file while another portal slot can still write to it.
            val oldSlots = slots.filter { it.filePath == oldFile.absolutePath }
            oldSlots.all { oldSlot ->
                if (oldSlot.portalSlot >= 0 && !SkylanderConfig.removeSkylander(oldSlot.portalSlot)) {
                    false
                } else {
                    oldSlot.clear()
                    true
                }
            }
        }

        rescanCollection()
        rebuildRows()
        onClose?.invoke()
    }

    /**
     * Legt eine fertig ausgebaute Figur auf. Gibt es sie schon, wird die
     * vorhandene Datei genommen; sonst wird sie aus der Vorlage erschaffen.
     *
     * Die Vorlage ist der Klartext einer im Spiel voll ausgebauten Figur. Weil
     * die Pruefsummen des Kopfblocks im Klartext stehen und mitkopiert werden,
     * bleiben sie gueltig. Gold und Stufe setzt Dolphin danach selbst und
     * rechnet die Pruefsummen neu — das war auf der PS3 nicht moeglich.
     */
    private fun placeMaxed(figure: CatalogFigure) {
        val vorhanden = maxedFiles[figure.id to figure.variant]
        if (vorhanden != null) {
            placeFile(figure, vorhanden)
            return
        }

        val template = SkylanderCatalog.getTemplate(requireContext())?.copyOf()
        if (template == null) {
            setStatus("Vorlage fehlt. Ohne sie gibt es keine ultimativen Figuren.")
            return
        }

        val target = activeSlot
        val slot = slots[target]
        val vorher = slot.figure?.name

        // Erfahrung und Gold in beide Speicherbereiche schreiben. Die
        // Pruefsummen rechnet die Bruecke danach neu, deshalb darf hier alles
        // veraendert werden.
        writeShort(template, AREA_A + OFF_EXPERIENCE, MAXED_EXPERIENCE)
        writeShort(template, AREA_B + OFF_EXPERIENCE, MAXED_EXPERIENCE)
        writeShort(template, AREA_A + OFF_MONEY, MAXED_MONEY)
        writeShort(template, AREA_B + OFF_MONEY, MAXED_MONEY)

        // Die zusaetzliche Faehigkeit der Series-2-Figuren steht nicht im
        // Ausbauwort — das war schon voll, als das Spiel sie noch zum Kauf
        // anbot. Gemessen an einem Eruptor (S2) vor und nach dem Kauf: es
        // aenderten sich genau diese drei Stellen von null auf ein gesetztes
        // Bit. Vermutung, kein Beweis: der Nutzer hat zwischen den beiden
        // Aufnahmen gespielt, und dabei schreibt das Spiel auch Fortschritt.
        for (area in intArrayOf(AREA_A, AREA_B)) {
            for ((offset, bit) in WOW_POW_BITS) {
                template[area + offset] = (template[area + offset].toInt() or bit).toByte()
            }
        }

        val file = SkyCollection.maxedFileFor(userDirectory(), figure, upperPath)
        file.parentFile?.mkdirs()

        val created = SkylanderConfig.createMaxedSkylander(
            figure.id,
            figure.variant,
            file.absolutePath,
            slot.portalSlot,
            template,
            if (upperPath) UPGRADES_UPPER else UPGRADES_LOWER,
            -1,
            -1
        )

        val portalSlot = created?.first
        if (portalSlot == null) {
            setStatus(figure.name + " ließ sich nicht erschaffen.")
            return
        }

        slot.portalSlot = portalSlot
        slot.figure = figure
        slot.filePath = file.absolutePath

        rescanCollection()
        rebuildRows()
        onClose?.invoke()
    }

    private fun writeShort(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun ejectSlot(index: Int) {
        val slot = slots[index]
        if (slot.portalSlot < 0) {
            setStatus("Platz " + (index + 1) + " ist leer.")
            return
        }

        SkylanderConfig.removeSkylander(slot.portalSlot)
        val name = slot.figure?.name ?: "Figur"
        slot.clear()

        rebuildRows()
        setStatus(name + " vom Portal genommen.")
        peek(REMOVE_HIDE_MS)
    }

    private fun clearPortal() {
        var removed = 0
        for (index in 0 until SLOT_COUNT) {
            val slot = slots[index]
            if (slot.portalSlot >= 0) {
                SkylanderConfig.removeSkylander(slot.portalSlot)
                slot.clear()
                removed++
            }
        }

        rebuildRows()
        setStatus(
            if (removed > 0) removed.toString() + " Figur(en) vom Portal genommen."
            else "Auf dem Portal liegt nichts."
        )
        if (removed > 0) peek(REMOVE_HIDE_MS)
    }

    // -------------------------------------------------------------- Löschmodus

    /**
     * Start bedeutet je nach Ansicht etwas anderes: in der Sammlung den
     * Loeschmodus, bei den ultimativen den Faehigkeitspfad. Beim Beschwoeren
     * gibt es nichts einzustellen.
     */
    private fun toggleDeleteMode() {
        if (mode == Mode.MAXED) {
            upperPath = !upperPath
            rescanCollection()
            rebuildRows()
            setStatus(if (upperPath) "Oberer Pfad gewählt." else "Unterer Pfad gewählt.")
            return
        }

        if (mode != Mode.COLLECTION) {
            setStatus("Beschwören erschafft immer neue Figuren auf Stufe 0.")
            return
        }

        deleteMode = !deleteMode
        marked.clear()
        confirming = false
        rebuildRows()
        setStatus(
            if (deleteMode) "Löschmodus: Kreuz merkt vor, Quadrat löscht."
            else "Löschmodus beendet."
        )
    }

    private fun askDelete() {
        if (marked.isEmpty()) {
            setStatus("Nichts vorgemerkt.")
            return
        }
        confirming = true
        updateButtonBar()
        setStatus(marked.size.toString() + " Datei(en) endgültig löschen? Kreuz bestätigt.")
    }

    private fun performDelete() {
        var deleted = 0
        for (path in marked) {
            // Nichts loeschen, was gerade auf dem Portal liegt: das waere ein
            // Spielstand, den das Spiel in diesem Moment beschreibt.
            if (slotOfFile(path) >= 0) continue
            if (File(path).delete()) deleted++
        }

        val skipped = marked.size - deleted
        marked.clear()
        confirming = false
        deleteMode = false

        rescanCollection()
        rebuildRows(resetCursor = true)
        setStatus(
            deleted.toString() + " gelöscht." +
                if (skipped > 0) " " + skipped + " liegen auf dem Portal und blieben." else ""
        )
    }

    // ----------------------------------------------------------------- Filter

    private fun cycleElement(direction: Int) {
        elementFilter = (elementFilter + direction + ELEMENTS.size) % ELEMENTS.size
        rebuildRows(resetCursor = true)
    }

    private fun toggleVariants() {
        allVariants = !allVariants
        rebuildRows(resetCursor = true)
        setStatus(
            if (allVariants) "Alle Varianten, auch Sonderfassungen."
            else "Nur Grundfigur und Series 2."
        )
    }

    private fun cycleGame() {
        gameFilter = (gameFilter + 1) % GAMES.size
        rebuildRows(resetCursor = true)
    }

    private fun toggleMode() {
        switchTo(
            when (mode) {
                Mode.COLLECTION -> Mode.SUMMON
                Mode.SUMMON -> Mode.MAXED
                Mode.MAXED -> Mode.ITEMS
                Mode.ITEMS -> Mode.COLLECTION
            }
        )
    }

    private fun restoreCursor() {
        val remembered = savedCursors[mode.ordinal]
        if (remembered in 0 until adapter.itemCount) {
            adapter.selected = remembered
            binding.gridFigures.scrollToPosition(remembered)
            adapter.rowAt(remembered)?.let {
                setStatus(it.figure.name + "  -  " + it.figure.gameLabel)
            }
        }
    }

    private fun switchTo(target: Mode) {
        if (target == mode) return
        savedCursors[mode.ordinal] = adapter.selected
        mode = target
        deleteMode = false
        confirming = false
        marked.clear()
        focus = Focus.GRID
        rebuildRows(resetCursor = true)
        restoreCursor()
        setStatus(
            when (mode) {
                Mode.COLLECTION -> "Eigene Sammlung."
                Mode.SUMMON -> "Alle Figuren. Kreuz erschafft eine neue Datei."
                Mode.ITEMS -> "Gegenstände, Fahrzeuge, Fallen und Trophäen."
                Mode.MAXED -> "Ultimative Figuren, " +
                    (if (upperPath) "oberer Pfad" else "unterer Pfad") +
                    ". Options wechselt den Pfad."
            }
        )
    }

    // ---------------------------------------------------------------- Auswahl

    /**
     * Bewegt die Markierung. Eigene Rechnung statt Androids Fokussuche, damit
     * das Steuerkreuz auch als Achse funktioniert.
     */
    private fun move(dx: Int, dy: Int) {
        if (focus == Focus.PORTAL) {
            when {
                dx != 0 -> {
                    activeSlot = (activeSlot + dx + SLOT_COUNT) % SLOT_COUNT
                    updateSlots()
                    updateButtonBar()
                }

                dy > 0 -> {
                    focus = Focus.GRID
                    updateSlots()
                    updateButtonBar()
                }
            }
            return
        }

        val count = adapter.itemCount
        if (count == 0) {
            if (dy < 0) {
                focus = Focus.PORTAL
                updateSlots()
                updateButtonBar()
            }
            return
        }

        val current = adapter.selected.coerceAtLeast(0)
        if (dy < 0 && current < columns) {
            focus = Focus.PORTAL
            updateSlots()
            updateButtonBar()
            return
        }

        var next = current + dx + dy * columns
        if (dx != 0) {
            next = next.coerceIn(0, count - 1)
        } else if (dy > 0 && next >= count) {
            // Die letzte Reihe ist oft kuerzer. Statt stehen zu bleiben auf die
            // letzte Karte gehen — aber nur, wenn man nicht schon dort steht.
            if (current == count - 1) return
            next = count - 1
        }
        if (next < 0 || next >= count) return

        adapter.selected = next
        binding.gridFigures.scrollToPosition(next)
        adapter.rowAt(next)?.let { setStatus(it.figure.name + "  -  " + it.figure.gameLabel) }
    }

    // ------------------------------------------------------------------ Peek

    /**
     * Stellt das Menue kurz beiseite, damit die Beschwoerung im Spiel zu sehen
     * ist. Beim Abnehmen kuerzer als beim Auflegen — dort gibt es keine
     * Animation, nur das Verschwinden.
     */
    private fun peek(millis: Long) {
        hiddenTimed = true
        binding.root.visibility = View.INVISIBLE
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            hiddenTimed = false
            _binding?.root?.visibility = View.VISIBLE
        }, millis)
    }

    /** L2: beiseite, bis die naechste Taste kommt. */
    private fun peekHold() {
        hiddenUntilKey = true
        handler.removeCallbacksAndMessages(null)
        hiddenTimed = false
        binding.root.visibility = View.INVISIBLE
    }

    private fun unpeek() {
        hiddenUntilKey = false
        hiddenTimed = false
        handler.removeCallbacksAndMessages(null)
        _binding?.root?.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------- Controller

    /**
     * Tasten des Gamepads. Wird von EmulationActivity aufgerufen, bevor die
     * Eingabe an den Emulatorkern geht — sonst steuert man das Spiel statt das
     * Menue.
     *
     * @return true, wenn die Taste verbraucht ist.
     */
    fun onControllerKey(event: KeyEvent, player: Int): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || _binding == null) return true
        if (player != activePlayer) selectPlayer(player)

        // Solange das Menue beiseite steht, holt der naechste Druck es zurueck.
        if (hiddenUntilKey || hiddenTimed) {
            unpeek()
            return true
        }

        if (confirming) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> performDelete()
                KeyEvent.KEYCODE_BUTTON_B -> {
                    confirming = false
                    updateButtonBar()
                    setStatus("Abgebrochen. Es wurde nichts gelöscht.")
                }
            }
            return true
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> accept()
            KeyEvent.KEYCODE_BUTTON_B -> onClose?.invoke()
            KeyEvent.KEYCODE_BUTTON_Y -> toggleMode()

            KeyEvent.KEYCODE_BUTTON_X ->
                if (deleteMode) askDelete() else clearPortal()

            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> toggleDeleteMode()

            KeyEvent.KEYCODE_BUTTON_L1 -> cycleElement(-1)
            KeyEvent.KEYCODE_BUTTON_R1 -> cycleElement(1)
            KeyEvent.KEYCODE_BUTTON_L2 -> peekHold()
            KeyEvent.KEYCODE_BUTTON_R2 -> cycleGame()
            KeyEvent.KEYCODE_BUTTON_SELECT -> toggleVariants()

            KeyEvent.KEYCODE_DPAD_LEFT -> move(-1, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(1, 0)
            KeyEvent.KEYCODE_DPAD_UP -> move(0, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(0, 1)
        }
        return true
    }

    /**
     * Sticks und Steuerkreuz. Das Steuerkreuz des DualShock 4 kommt als
     * Achsenpaar HAT_X/HAT_Y, nicht als Taste.
     */
    fun onControllerMotion(event: MotionEvent, player: Int): Boolean {
        if (_binding == null) return true

        val now = System.currentTimeMillis()
        if (now - lastStickMove < STICK_REPEAT_MS) return true

        val x = event.getAxisValue(MotionEvent.AXIS_X) + event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y) + event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        val dx = if (x < -STICK_THRESHOLD) -1 else if (x > STICK_THRESHOLD) 1 else 0
        val dy = if (y < -STICK_THRESHOLD) -1 else if (y > STICK_THRESHOLD) 1 else 0
        if (dx == 0 && dy == 0) return true

        if (player != activePlayer) selectPlayer(player)
        lastStickMove = now
        if (hiddenUntilKey || hiddenTimed) {
            unpeek()
            return true
        }

        if (dy != 0) move(0, dy) else move(dx, 0)
        return true
    }

    /** Ein Portalplatz. */
    private class Slot {
        var portalSlot: Int = -1
        var figure: CatalogFigure? = null
        var filePath: String? = null

        fun clear() {
            portalSlot = -1
            figure = null
            filePath = null
        }
    }

    companion object {
        const val TAG = "SkylanderPortalFragment"

        /** Plaetze, die das Menue zeigt. Das Portal selbst koennte sechzehn. */
        const val SLOT_COUNT = 4

        private const val COLUMN_WIDTH_DP = 110
        private const val STICK_THRESHOLD = 0.6f
        private const val STICK_REPEAT_MS = 160L

        /** So lange bleibt das Menue weg, damit die Beschwoerung zu sehen ist. */
        /**
         * Gold, das eine ultimative Figur mitbringt. Dolphin rechnet die
         * Pruefsummen neu, deshalb ist der Wert frei waehlbar — im PS3-Projekt
         * musste er beim Wert der Vorlage bleiben. -1 behaelt die Vorlage.
         */
        private const val MAXED_MONEY = 50000

        /**
         * 33000 ist die Obergrenze von Spyro's Adventure und ergibt Stufe 10.
         * Giants ginge bis 15; wo diese hoeheren Punkte stehen, ist nicht
         * gemessen — in das 16-Bit-Feld hier passen sie nicht.
         */
        private const val MAXED_EXPERIENCE = 33000

        /** Kopfbloecke der beiden Speicherbereiche in der Vorlage. */
        /**
         * Ab diesem niedrigen Byte der Variante beginnen die Sonderfassungen.
         * Darunter liegen nur die Grundfigur (0x00) und Series 2 (0x01).
         */
        private const val FIRST_SPECIAL = 0x02

        private const val AREA_A = 0x00
        private const val AREA_B = 0xB0
        private const val OFF_EXPERIENCE = 0x00
        private const val OFF_MONEY = 0x03

        /** Stelle im Bereich und Bit, gemessen an Eruptor (S2). */
        private val WOW_POW_BITS = listOf(0x76 to 0x01, 0x88 to 0x40, 0x8B to 0x10)

        /**
         * Das Ausbauwort: Bit 0 heisst „ein Pfad ist gewaehlt", Bit 1 „es ist
         * der untere", Bits 2 bis 8 die Faehigkeiten, Bit 9 der Seelenstein.
         *
         * Bits 10 und 11 gehoeren zur letzten Faehigkeit nach dem Seelenstein.
         * Am Geraet gemessen: mit 0x03FD fehlte sie, mit 0x07FD ebenfalls.
         * Bouncer, im PS3-Projekt die im Spiel voll ausgebaute Vorlage, trug
         * 0x0FFD — also beide Bits. Genau dieser Wert wird jetzt benutzt.
         */
        private const val UPGRADES_UPPER = 0x0FFD
        private const val UPGRADES_LOWER = 0x0FFF

        /** Fallen, Fahrzeuge und Gegenstaende lassen sich nicht ausbauen. */
        private val MAXABLE_TYPES = setOf("Skylander", "Giant", "Swapper", "TrapMaster", "Mini")

        /** Alles, was keine spielbare Figur ist: eigener Reiter. */
        private val ITEM_TYPES = setOf("Item", "Vehicle", "Trap", "Trophy")

        private val ITEM_LABELS = mapOf(
            "Item" to "Gegenstand",
            "Vehicle" to "Fahrzeug",
            "Trap" to "Falle",
            "Trophy" to "Trophäe"
        )

        private const val PLACE_HIDE_MS = 4500L
        private const val REMOVE_HIDE_MS = 1200L

        /** Leerer erster Eintrag heisst "Alle". Reihenfolge wie in Dolphins enum. */
        private val ELEMENTS = listOf(
            "", "Magic", "Fire", "Air", "Life", "Undead", "Earth", "Water", "Tech",
            "Dark", "Light", "Other"
        )

        private val GERMAN_ELEMENTS = mapOf(
            "Magic" to "Magie", "Fire" to "Feuer", "Air" to "Luft", "Life" to "Leben",
            "Undead" to "Untot", "Earth" to "Erde", "Water" to "Wasser",
            "Tech" to "Technik", "Dark" to "Dunkel", "Light" to "Licht",
            "Other" to "Andere"
        )

        private val GAMES = listOf(
            "", "SpyrosAdv", "Giants", "SwapForce", "TrapTeam", "Superchargers"
        )

        /**
         * Der Portalzustand lebt im C++-Kern und ueberlebt dieses Fragment.
         * Deshalb wird die Zuordnung Platz zu Figur hier statisch gehalten.
         */
        private val slots = Array(SLOT_COUNT) { Slot() }

        /**
         * Zwischen zwei Aufrufen gemerkt: welche Ansicht offen war, wie die
         * Filter standen, welcher Platz gewaehlt war und wo der Zeiger in jeder
         * Ansicht stand.
         */
        private var savedMode = Mode.COLLECTION
        private var savedElement = 0
        private var savedGame = 0
        private var savedSlot = 0
        private var savedUpperPath = false
        private var savedAllVariants = false
        private val savedCursors = IntArray(Mode.entries.size) { -1 }

        fun newInstance(player: Int = 1): SkylanderPortalFragment =
            SkylanderPortalFragment().apply { activePlayer = player.coerceIn(1, 2) }
    }
}
