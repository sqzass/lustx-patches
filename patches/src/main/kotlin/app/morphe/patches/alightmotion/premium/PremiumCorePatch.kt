/*
 * Copyright 2026 sqzass.
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.alightmotion.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.matchSingle
import app.morphe.patches.alightmotion.shared.ALIGHT_5_0_270
import app.morphe.patches.alightmotion.shared.alightMethod
import app.morphe.patches.alightmotion.shared.forceGateResult
import app.morphe.patches.alightmotion.shared.singleInvoke
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val NR = "Lcom/alightcreative/account/Nr;"
private const val JY6 = "Lcom/alightcreative/account/Jy6;"
private const val SET_CONTAINS = "Ljava/util/Set;->contains"

/**
 * Premium core: the two account-layer gates Tanryu forced (5.0.270.1002578).
 *
 * 1. account/Nr.kB(FirebaseAuth)V — calls Nr.b2()Z and forces its move-result to 1 before
 *    IAPMiddleware.Zjm(Z)V consumes it.
 * 2. account/Jy6.Ud(Set)I — two Set.contains()Z gates, both forced to 1.
 *
 * Anchors are exact obfuscated descriptors from the locked build. Every matchSingle()
 * asserts exactly one target method; version drift throws PatchException instead of
 * silently under-patching.
 */
private object NrKb : Fingerprint(
    definingClass = NR,
    name = "kB",
    returnType = "V",
    parameters = listOf("Lcom/google/firebase/auth/FirebaseAuth;"),
    filters = listOf(
        methodCall(definingClass = "Lcom/alightcreative/account/IAPMiddleware;", name = "Zjm"),
    ),
)

private object Jy6Ud : Fingerprint(
    definingClass = JY6,
    name = "Ud",
    returnType = "I",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/util/Set;"),
)

@Suppress("unused")
val premiumCorePatch = bytecodePatch(
    name = "Unlock premium (core)",
    description = "Forces the core account-layer membership gates on.",
    default = true,
) {
    compatibleWith(ALIGHT_5_0_270)

    execute {
        // --- account/Nr.kB(FirebaseAuth)V ------------------------------------
        NrKb.matchSingle()
        val kb = alightMethod(NR, "kB", listOf("Lcom/google/firebase/auth/FirebaseAuth;"), "V")
        val b2Idx = kb.singleInvoke(NR, "b2")
        kb.forceGateResult(b2Idx)

        // --- account/Jy6.Ud(Set)I --------------------------------------------
        Jy6Ud.matchSingle()
        val ud = alightMethod(JY6, "Ud", listOf("Ljava/util/Set;"), "I")
        val impl = ud.implementation
            ?: throw PatchException("alightmotion: Jy6.Ud has no implementation")
        val containsHits = impl.instructions.withIndex().mapNotNull { indexed ->
            val ins = indexed.value
            val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference
            if (ref != null && "${ref.definingClass}->${ref.name}" == SET_CONTAINS) indexed.index else null
        }
        if (containsHits.size != 2) {
            throw PatchException("alightmotion: Jy6.Ud Set.contains() x${containsHits.size}, expected 2")
        }
        // Back-to-front: inserting a const shifts later indexes, so process the
        // higher anchor first and never invalidate the lower one.
        for (idx in containsHits.sortedDescending()) {
            ud.forceGateResult(idx)
        }
    }
}
