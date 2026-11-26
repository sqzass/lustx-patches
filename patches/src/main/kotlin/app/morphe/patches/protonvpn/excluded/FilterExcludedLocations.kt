/*
 * Copyright 2026 sqzass.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Recon-verified against Proton VPN 5.21.57.0 (Play, installed build):
 *   The excluded locations picker loads countries via ServerManager.f(Z),
 *   bypassing the profiles-filtered m() method. This patch filters f(Z)'s
 *   return value to only free countries, matching the countries tab behavior.
 */

package app.morphe.patches.protonvpn.excluded

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode

private const val SERVER_MANAGER = "Lcom/protonvpn/android/utils/ServerManager;"

private object ServerManagerFZingerprint : Fingerprint(
    definingClass = SERVER_MANAGER,
    name = "f",
    returnType = "Ljava/util/List;",
    parameters = listOf("Z"),
)

internal fun filterExcludedLocations() {
    val method = ServerManagerFZingerprint.match().method
    val instructions = method.instructions.toList()

    // Find both return-object instructions
    val returnIndices = instructions.indices.filter { i ->
        instructions[i].opcode == Opcode.RETURN_OBJECT
    }
    if (returnIndices.size != 2) {
        throw PatchException("ProtonVPN: expected 2 return-object in ServerManager.f(Z), found ${returnIndices.size}")
    }

    // For each return, wrap the result with hxCountriesForAccount
    for (returnIdx in returnIndices) {
        val moveResult = instructions[returnIdx - 1]
        if (moveResult.opcode != Opcode.MOVE_RESULT_OBJECT) {
            continue
        }
        val reg = (moveResult as com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction).registerA
        method.addInstruction(
            returnIdx,
            "invoke-static { v$reg }, $SERVER_MANAGER->hxCountriesForAccount(Ljava/util/List;)Ljava/util/List;",
        )
        method.addInstruction(returnIdx + 1, "move-result-object v$reg")
    }

    println("[protonvpn] excluded: filtered ServerManager.f(Z) to free countries only")
}
