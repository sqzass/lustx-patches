/*
 * Copyright 2026 sqzass.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Recon-verified against Proton VPN 5.21.57.0 (Play, installed build):
 *   ServerManager->f(Z)Ljava/util/List;
 *   -> returns Lsl/s;->c or Lsl/s;->d
 *   -> excluded-locations picker country list
 *
 * This wraps both RETURN_OBJECT sites with the already-verified
 * hxCountriesForAccount helper injected by ProfilesHost.
 */

package app.morphe.patches.protonvpn.excluded

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.protonvpn.shared.helpers.injectProfilesInfrastructure
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val SERVER_MANAGER = "Lcom/protonvpn/android/utils/ServerManager;"

private object ServerManagerFZingerprint : Fingerprint(
    definingClass = SERVER_MANAGER,
    name = "f",
    returnType = "Ljava/util/List;",
    parameters = listOf("Z"),
)

internal fun filterExcludedLocations() {
    injectProfilesInfrastructure()

    val method = ServerManagerFZingerprint.match().method
    val insns = method.instructions.toList()

    val returnIndices = insns.indices.filter { i ->
        insns[i].opcode == Opcode.RETURN_OBJECT
    }

    if (returnIndices.size != 2) {
        throw PatchException(
            "ProtonVPN: expected 2 return-object in ServerManager.f(Z), found ${returnIndices.size}",
        )
    }

    // Insert backwards so earlier return indices are not shifted by later insertions.
    for (returnIdx in returnIndices.sortedDescending()) {
        val prev = insns[returnIdx - 1]
        if (prev.opcode != Opcode.MOVE_RESULT_OBJECT) continue

        val reg = (prev as OneRegisterInstruction).registerA

        method.addInstruction(
            returnIdx,
            "invoke-static { v$reg }, $SERVER_MANAGER->hxCountriesForAccount(Ljava/util/List;)Ljava/util/List;",
        )
        method.addInstruction(returnIdx + 1, "move-result-object v$reg")
    }

    println("[protonvpn] excluded: filtered ServerManager.f(Z) to free countries only")
}
