/*
 * Copyright 2026 sqzass.
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.alightmotion.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private val MOVE_RESULT_OPCODES = listOf(Opcode.MOVE_RESULT, Opcode.MOVE_RESULT_WIDE, Opcode.MOVE_RESULT_OBJECT)

/**
 * Require exactly one method matching [cls]->[name]([params])[ret]; throw [PatchException]
 * otherwise. The app obfuscates every release, so silent lookups are not acceptable here.
 */
fun BytecodePatchContext.alightMethod(
    cls: String,
    name: String,
    parameters: List<String>,
    returnType: String,
): MutableMethod = mutableClassDefByOrNull(cls)
    ?.methods
    ?.singleOrNull {
        it.name == name && it.parameterTypes == parameters && it.returnType == returnType
    } ?: throw PatchException(
    "alightmotion: method not found exactly once: $cls->$name(${parameters.joinToString(",")}):$returnType " +
        "(wrong base? suite is locked to 5.0.270.1002578)",
)

/**
 * Index of the single invoke of [owner]->[name] in this method. Throws if the invoke is
 * absent or present more than once — anchors must be unique per method.
 */
fun MutableMethod.singleInvoke(owner: String, name: String): Int {
    val impl = implementation
        ?: throw PatchException("alightmotion: $owner->$name invoke site: method has no implementation")
    val hits = impl.instructions.withIndex().mapNotNull { indexed ->
        val ins = indexed.value
        val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference
        if (ref != null && ref.definingClass == owner && ref.name == name) indexed.index else null
    }
    if (hits.size != 1) {
        throw PatchException("alightmotion: $owner->$name invoked ${hits.size} times, expected exactly 1")
    }
    return hits.first()
}

/**
 * Force the boolean result of the invoke at [invokeIndex] to [value] (default 1).
 *
 * Stock shape: `invoke-XXX … -> move-result vN`. Inserts `const/4 vN, 0x1` (or `const/16`
 * / `const` for high registers) immediately after the move-result: same register, nothing
 * deleted, no register growth. The original call still runs; only the branch consuming the
 * result sees the forced value.
 *
 * Idempotent: skips when that const is already in place (another bundle may have forced it).
 */
fun MutableMethod.forceGateResult(invokeIndex: Int, value: Int = 1) {
    val impl = implementation
        ?: throw PatchException("alightmotion: cannot force gate result, method has no implementation")
    val mrIdx = invokeIndex + 1
    val moveResult = impl.instructions.getOrNull(mrIdx)
        ?: throw PatchException("alightmotion: gate invoke at $invokeIndex not followed by move-result*")
    if (moveResult.opcode !in MOVE_RESULT_OPCODES) {
        throw PatchException("alightmotion: instruction after gate invoke is not move-result*")
    }
    val reg = (moveResult as OneRegisterInstruction).registerA
    val alreadyForced = impl.instructions.getOrNull(mrIdx + 1)?.let {
        it is NarrowLiteralInstruction &&
            it is OneRegisterInstruction &&
            it.registerA == reg &&
            it.narrowLiteral == 1
    } == true
    if (alreadyForced) return
    val op = when {
        reg < 16 -> "const/4"
        reg < 256 -> "const/16"
        else -> "const"
    }
    addInstruction(mrIdx + 1, "$op v$reg, 0x${value.toString(16)}")
}
