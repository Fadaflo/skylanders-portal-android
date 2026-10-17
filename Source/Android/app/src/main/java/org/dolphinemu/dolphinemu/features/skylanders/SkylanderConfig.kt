// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders

import android.util.Pair
import org.dolphinemu.dolphinemu.features.skylanders.model.SkylanderPair

object SkylanderConfig {
    var LIST_SKYLANDERS: Map<SkylanderPair, String> = getSkylanderMap()
    var REVERSE_LIST_SKYLANDERS: Map<String, SkylanderPair> = getInverseSkylanderMap()

    private external fun getSkylanderMap(): Map<SkylanderPair, String>
    private external fun getInverseSkylanderMap(): Map<String, SkylanderPair>

    @JvmStatic
    external fun removeSkylander(slot: Int): Boolean

    @JvmStatic
    external fun loadSkylander(slot: Int, fileName: String?): Pair<Int?, String?>?

    /** Read only: -1 unreadable, 0 no path, 1 upper, 2 lower. */
    @JvmStatic
    external fun readUpgradePath(fileName: String): Int

    /**
     * Erschafft eine fertig ausgebaute Figur aus einer Vorlage.
     *
     * @param plaintext Klartext der 22 Spielstandsbloecke, 352 Byte
     * @param upgradesWord Ausbauwort fuer beide Bereiche, oder -1 unveraendert
     * @param money Gold, oder -1 fuer den Wert der Vorlage
     * @param heroLevel Heldenstufe, oder -1 fuer den Wert der Vorlage
     */
    @JvmStatic
    external fun createMaxedSkylander(
        id: Int,
        variant: Int,
        fileName: String,
        slot: Int,
        plaintext: ByteArray,
        upgradesWord: Int,
        money: Int,
        heroLevel: Int
    ): Pair<Int, String>?

    @JvmStatic
    external fun createSkylander(
        id: Int,
        variant: Int,
        fileName: String,
        slot: Int
    ): Pair<Int, String>
}
