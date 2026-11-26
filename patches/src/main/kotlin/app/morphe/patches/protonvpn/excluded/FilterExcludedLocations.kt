/*
 * Copyright 2026 sqzass.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Wraps both return sites in ServerManager.f(Z) with hxCountriesForAccount().
 */

package app.morphe.patches.protonvpn.excluded

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.protonvpn.shared.helpers.injectProfilesInfrastructure
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

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

    val returns = insns.indices.filter { insns[it].opcode == Opcode.RETURN_OBJECT }
    if (returns.size != 2) {
        throw PatchException(
            "ProtonVPN: expected 2 return-object sites in ServerManager.f(Z), found ${returns.size}",
        )
    }

    var wrapped = 0

    for (returnIndex in returns.sortedDescending()) {
        val load = insns[returnIndex - 1]
        if (load.opcode != Opcode.IGET_OBJECT) {
            throw PatchException(
                "ProtonVPN: expected iget-object before return-object at $returnIndex in ServerManager.f(Z)",
            )
        }

        val listReg = (load as TwoRegisterInstruction).registerA

        method.addInstruction(
            returnIndex,
            "invoke-static { v$listReg }, $SERVER_MANAGER->hxCountriesForAccount(Ljava/util/List;)Ljava/util/List;",
        )
        method.addInstruction(returnIndex + 1, "move-result-object v$listReg")
        wrapped++
    }

    if (wrapped != 2) {
        throw PatchException("ProtonVPN: failed to wrap both ServerManager.f(Z) returns, wrapped=$wrapped")
    }

    println("[protonvpn] excluded: wrapped 2 return sites in ServerManager.f(Z)")
}
