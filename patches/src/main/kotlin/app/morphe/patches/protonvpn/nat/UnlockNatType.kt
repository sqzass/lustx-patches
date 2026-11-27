/*
 * Copyright 2026 sqzass.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.protonvpn.nat

import app.morphe.patches.protonvpn.shared.helpers.unlockBooleanSettingRow

internal fun unlockNatType() {
    unlockBooleanSettingRow(
        getterName = "getRandomizedNat",
        logTag = "nat",
    )
}
