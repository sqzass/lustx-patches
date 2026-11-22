/*
 * Copyright 2026 sqzass.
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.portdroid.pro

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.mutableClassDefByOrNull
import app.morphe.patcher.util.getReference
import app.morphe.patches.all.shared.forceBooleanOnce
import app.morphe.patches.portdroid.tamper.bypassTamperCheckPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val SETTINGS = "Lcom/stealthcopter/portdroid/core/Settings;"
private const val IAP_STATUS = "Lcom/stealthcopter/portdroid/feature/upgrade/IAPViewModel\$IAPStatus;"

@Suppress("unused")
val unlockProPatch = bytecodePatch(
    name = "Unlock Pro",
    description = "Unlocks PortDroid Pro features (forces the pro gate and billing status).",
) {
    compatibleWith(
        Compatibility(
            name = "PortDroid - Network Analysis",
            packageName = "com.stealthcopter.portdroid",
            targets = listOf(
                AppTarget(version = "1.0.7", versionCode = 113),
                AppTarget(version = "1.3.0", versionCode = 140),
            ),
        ),
    )

    dependsOn(bypassTamperCheckPatch)

    execute {
        // Gate 1: Settings.getProVersion()Z — the primary feature gate (both versions).
        val gpv = mutableClassDefByOrNull(SETTINGS)
            ?.methods?.singleOrNull {
                it.name == "getProVersion" && it.returnType == "Z" && it.parameterTypes.isEmpty()
            } ?: throw PatchException("PortDroid: Settings.getProVersion()Z not found (version drift?)")
        gpv.forceBooleanOnce(true, "Settings.getProVersion")

        // Gate 2: IAPStatus.proVersion field assignment. Anchor on the iput-boolean
        // itself (signature changed 1.0.7 -> 1.3.0), force its source register to 1
        // immediately before the store. Take the LAST hit = the effective assignment
        // (stays correct even if another bundle injected an earlier forced iput).
        val iap = mutableClassDefByOrNull(IAP_STATUS)
            ?: throw PatchException("PortDroid: IAPViewModel\$IAPStatus not found")
        val hits = mutableListOf<Pair<app.morphe.patcher.util.proxy.mutableTypes.MutableMethod, Int>>()
        for (m in iap.methods) {
            if (m.name != "<init>") continue
            val impl = m.implementation ?: continue
            impl.instructions.forEachIndexed { i, ins ->
                if (ins.opcode == Opcode.IPUT_BOOLEAN) {
                    val ref = ins.getReference<FieldReference>()
                    if (ref?.definingClass == IAP_STATUS && ref.name == "proVersion") hits.add(m to i)
                }
            }
        }
        if (hits.isEmpty()) {
            throw PatchException("PortDroid: IAPStatus.proVersion assignment not found (version drift?)")
        }
        val (m, idx) = hits.last()
        val src = (m.getInstruction(idx) as TwoRegisterInstruction).registerA
        val prev = if (idx > 0) m.getInstruction(idx - 1) else null
        val already = prev is NarrowLiteralInstruction && prev is OneRegisterInstruction &&
            prev.registerA == src && prev.narrowLiteral == 1
        if (!already) {
            val op = if (src < 16) "const/4" else "const/16"
            m.addInstruction(idx, "$op v$src, 0x1")
        }
    }
}