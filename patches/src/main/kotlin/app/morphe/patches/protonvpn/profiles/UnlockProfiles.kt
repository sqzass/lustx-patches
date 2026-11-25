/*
 * Copyright 2026 sqzass.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Based on hxreborn/morphe-patches (protonvpn/misc/profiles/UnlockProfilesPatch.kt).
 * Phase 3C: unlocks profiles on free plans, filters to free locations only.
 */

package app.morphe.patches.protonvpn.profiles

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.protonvpn.shared.fingerprints.ProfilesListToStringFingerprint
import app.morphe.patches.protonvpn.shared.fingerprints.SERVER_MANAGER
import app.morphe.patches.protonvpn.shared.fingerprints.profileAvailabilityFingerprint
import app.morphe.patches.protonvpn.shared.fingerprints.profileCitiesOrStatesFingerprint
import app.morphe.patches.protonvpn.shared.fingerprints.profileServerFilterFingerprint
import app.morphe.patches.protonvpn.shared.fingerprints.profileTypesToStringFingerprints
import app.morphe.patches.protonvpn.shared.fingerprints.vpnCountriesFingerprint
import app.morphe.patches.protonvpn.shared.helpers.injectFreeServerInfrastructure
import app.morphe.patches.protonvpn.shared.helpers.injectProfilesInfrastructure
import app.morphe.patches.protonvpn.shared.restrictions.clearFreeUserCheck
import app.morphe.patches.protonvpn.shared.restrictions.filterConstructorProperty
import app.morphe.patches.protonvpn.shared.restrictions.filterReturnValue
import app.morphe.patches.protonvpn.shared.restrictions.invertFreeServerCheckForFreeAccount
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal fun unlockProfiles() {
    // Ensure shared infrastructure is present (idempotent — safe if ShowFreeServerLocations ran first).
    injectFreeServerInfrastructure()
    injectProfilesInfrastructure()

    // 1. Availability check: disable free-user gate
    clearFreeUserCheck(profileAvailabilityFingerprint())

    // 2. VPN countries list: filter to countries containing at least one free server
    vpnCountriesFingerprint().method.filterReturnValue(
        "$SERVER_MANAGER->hxCountriesForAccount(Ljava/util/List;)Ljava/util/List;",
    )

    // 3. Profile server filter: invert isFreeServer for free users
    invertFreeServerCheckForFreeAccount(profileServerFilterFingerprint())

    // 4. Per-country server list: filter to free servers
    profileCitiesOrStatesFingerprint().match().run {
        val serverList = instructionMatches.last()
        val register = serverList.getInstruction<OneRegisterInstruction>().registerA
        method.addInstructions(
            serverList.index + 1,
            """
                invoke-static { v$register }, $SERVER_MANAGER->hxServersForAccount(Ljava/util/List;)Ljava/util/List;
                move-result-object v$register
            """,
        )
    }

    // 5. Profiles list: drop UNAVAILABLE_PLAN entries
    filterConstructorProperty(
        ProfilesListToStringFingerprint,
        "profiles",
        "$SERVER_MANAGER->hxProfilesForAccount(Ljava/util/List;)Ljava/util/List;",
    )

    // 6. Profile type buttons: keep only Standard
    profileTypesToStringFingerprints.forEach {
        filterConstructorProperty(
            it,
            "availableTypes",
            "$SERVER_MANAGER->hxProfileTypesForAccount(Ljava/util/List;)Ljava/util/List;",
        )
    }

    println("[protonvpn] profiles: unlocked")
}
