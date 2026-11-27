/*
 * Copyright 2026 sqzass.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 *
 * Generic restricted-setting-row unlock.
 *
 * Anchors on a stable LocalUserSettings boolean getter inside invokeSuspend, then
 * forces the restricted Z argument of the following row constructor to 0.
 *
 * Safety:
 * - Never uses parameter registers as stash registers.
 * - Prefers replacing the Z register definition and restoring from its source.
 * - Falls back to stash/restore only when the stash register is locally safe.
 * - Skips instead of corrupting Settings when no safe transform is found.
 */

package app.morphe.patches.protonvpn.shared.helpers

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val LOCAL_USER_SETTINGS = "Lcom/protonvpn/android/settings/data/LocalUserSettings;"

private val WIDE_TYPES = setOf("J", "D")

private val MOVE_SET = setOf(
    Opcode.MOVE,
    Opcode.MOVE_FROM16,
    Opcode.MOVE_16,
    Opcode.MOVE_OBJECT,
    Opcode.MOVE_OBJECT_FROM16,
    Opcode.MOVE_OBJECT_16,
)

private val ONE_DEF = setOf(
    Opcode.CONST,
    Opcode.CONST_4,
    Opcode.CONST_16,
    Opcode.CONST_HIGH16,
    Opcode.CONST_WIDE,
    Opcode.CONST_WIDE_16,
    Opcode.CONST_WIDE_HIGH16,
    Opcode.MOVE_RESULT,
    Opcode.MOVE_RESULT_WIDE,
    Opcode.MOVE_RESULT_OBJECT,
    Opcode.NEW_INSTANCE,
    Opcode.SGET,
    Opcode.SGET_WIDE,
    Opcode.SGET_OBJECT,
    Opcode.SGET_BOOLEAN,
    Opcode.SGET_BYTE,
    Opcode.SGET_CHAR,
    Opcode.SGET_SHORT,
)

private val TWO_DEF = setOf(
    Opcode.MOVE,
    Opcode.MOVE_FROM16,
    Opcode.MOVE_16,
    Opcode.MOVE_OBJECT,
    Opcode.MOVE_OBJECT_FROM16,
    Opcode.MOVE_OBJECT_16,
    Opcode.IGET,
    Opcode.IGET_WIDE,
    Opcode.IGET_OBJECT,
    Opcode.IGET_BOOLEAN,
    Opcode.IGET_BYTE,
    Opcode.IGET_CHAR,
    Opcode.IGET_SHORT,
)

private val THREE_DEF = setOf(
    Opcode.AGET,
    Opcode.AGET_WIDE,
    Opcode.AGET_OBJECT,
    Opcode.AGET_BOOLEAN,
    Opcode.AGET_BYTE,
    Opcode.AGET_CHAR,
    Opcode.AGET_SHORT,
)

private val ONE_READ = setOf(
    Opcode.IF_EQ,
    Opcode.IF_NE,
    Opcode.IF_LT,
    Opcode.IF_GE,
    Opcode.IF_GT,
    Opcode.IF_LE,
    Opcode.IF_EQZ,
    Opcode.IF_NEZ,
    Opcode.IF_LTZ,
    Opcode.IF_GEZ,
    Opcode.IF_GTZ,
    Opcode.IF_LEZ,
    Opcode.ARRAY_LENGTH,
    Opcode.THROW,
    Opcode.MONITOR_ENTER,
    Opcode.MONITOR_EXIT,
)

internal fun unlockBooleanSettingRow(getterName: String, logTag: String) {
    val fingerprint = Fingerprint(
        name = "invokeSuspend",
        returnType = "Ljava/lang/Object;",
        parameters = listOf("Ljava/lang/Object;"),
        filters = listOf(
            methodCall(
                definingClass = LOCAL_USER_SETTINGS,
                name = getterName,
            ),
        ),
    )

    val match = fingerprint.matchOrNull() ?: run {
        println("[protonvpn] $logTag: $getterName invokeSuspend anchor not present, skipping")
        return
    }

    val method: MutableMethod = match.method
    val insns = method.instructions.toList()
    val getter = match.instructionMatches.first().index

    val registerCount = method.implementation?.registerCount ?: run {
        println("[protonvpn] $logTag: no register count, skipping")
        return
    }

    val paramWords = method.parameterTypes.sumOf {
        if (WIDE_TYPES.contains(it.toString())) 2 else 1
    } + if (!AccessFlags.STATIC.isSet(method.accessFlags)) 1 else 0

    val localCount = registerCount - paramWords

    if (localCount <= 0) {
        println("[protonvpn] $logTag: invalid local register count, skipping")
        return
    }

    val boundary = (getter + 1 until insns.size).firstOrNull { i ->
        isLocalUserSettingsCall(insns[i])
    } ?: insns.size

    var ctorIndex = -1
    var ctorRef: MethodReference? = null

    // Choose the last row constructor before the next LocalUserSettings call.
    // This avoids locking onto intermediate helper object constructors.
    for (i in getter + 1 until boundary) {
        val ins = insns[i]
        if (ins.opcode != Opcode.INVOKE_DIRECT_RANGE) continue

        val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference ?: continue
        if (ref.name != "<init>") continue

        val params = ref.parameterTypes.map { it.toString() }

        // Observed row shape:
        //   <init>(Object/Serializable, Z restricted, I title/resource, ...)
        if (params.size >= 3 && params[1] == "Z" && params[2] == "I") {
            ctorIndex = i
            ctorRef = ref
        }
    }

    val ctor = ctorRef ?: run {
        println("[protonvpn] $logTag: no restricted-row <init>(...,Z,I,...) before next setting getter, skipping")
        return
    }

    val range = insns[ctorIndex] as RegisterRangeInstruction

    // Range layout: objectref, param0, param1(Z), param2(I), ...
    // Restricted Z is param1, so absolute register is startRegister + 2.
    val zReg = range.startRegister + 2

    if (zReg >= localCount) {
        println("[protonvpn] $logTag: zReg=v$zReg is not a local register, skipping")
        return
    }

    val ctorRegisters = (range.startRegister until (range.startRegister + range.registerCount)).toSet()

    // Preferred path: replace the direct definition of the restricted Z register
    // and restore it from its original source immediately after the row ctor.
    val lastDef = (ctorIndex - 1 downTo getter + 1).firstOrNull { writes(insns[it], zReg) }

    if (lastDef != null) {
        val defIns = insns[lastDef]
        val srcReg = (defIns as? TwoRegisterInstruction)
            ?.takeIf { it.opcode in MOVE_SET && it.registerA == zReg }
            ?.registerB

        if (
            srcReg != null &&
            srcReg != zReg &&
            srcReg !in ctorRegisters &&
            (lastDef + 1 until ctorIndex).none { writes(insns[it], srcReg) }
        ) {
            method.replaceInstruction(lastDef, "const/16 v$zReg, 0x0")
            method.addInstruction(ctorIndex + 1, moveInsn(zReg, srcReg))

            println(
                "[protonvpn] $logTag: unlocked ${ctor.definingClass}-><init> " +
                    "(zReg=v$zReg, def@$lastDef, restored-from=v$srcReg)",
            )
            return
        }
    }

    // Fallback: zero before the first consumption of zReg, stash/restore safely.
    val zeroPoint = (getter + 1 until ctorIndex).firstOrNull { i ->
        reads(insns[i], zReg)
    } ?: ctorIndex

    val windowStart = zeroPoint
    val windowEnd = ctorIndex.coerceAtMost(insns.size - 1)

    val usedWindow = BooleanArray(localCount)
    for (i in windowStart..windowEnd) {
        markOperands(insns[i], usedWindow, localCount)
    }

    val restoreIndex = ctorIndex + 1

    val stash =
        ((zReg + 1) until localCount).firstOrNull { candidate ->
            !usedWindow[candidate] && stashSafeAfter(insns, restoreIndex, candidate)
        }
            ?: (0 until zReg).firstOrNull { candidate ->
                !usedWindow[candidate] && stashSafeAfter(insns, restoreIndex, candidate)
            }

    if (stash == null) {
        println("[protonvpn] $logTag: no safe local stash register for v$zReg, skipping")
        return
    }

    // Insert restore first, then stash + const at zeroPoint.
    method.addInstruction(ctorIndex + 1, moveInsn(zReg, stash))
    method.addInstruction(zeroPoint, "const/16 v$zReg, 0x0")
    method.addInstruction(zeroPoint, moveInsn(stash, zReg))

    println(
        "[protonvpn] $logTag: unlocked ${ctor.definingClass}-><init> " +
            "(zReg=v$zReg, zero@$zeroPoint, stash=v$stash, restore)",
    )
}

private fun isLocalUserSettingsCall(ins: Instruction): Boolean {
    val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return ref.definingClass == LOCAL_USER_SETTINGS
}

private fun moveInsn(dest: Int, src: Int): String = if (dest >= 16 || src >= 16) {
    "move/from16 v$dest, v$src"
} else {
    "move v$dest, v$src"
}

private fun stashSafeAfter(
    insns: List<Instruction>,
    startIndex: Int,
    reg: Int,
): Boolean {
    var firstRead = -1
    var firstWrite = -1

    for (i in startIndex until insns.size) {
        val ins = insns[i]

        if (firstRead < 0 && reads(ins, reg)) firstRead = i
        if (firstWrite < 0 && writes(ins, reg)) firstWrite = i

        if (firstRead >= 0 && firstWrite >= 0) break
    }

    // Safe if the register is dead after restore, or rewritten before it is read.
    return firstRead < 0 || (firstWrite >= 0 && firstWrite < firstRead)
}

private fun writes(ins: Instruction, reg: Int): Boolean = when (ins) {
    is OneRegisterInstruction -> ins.registerA == reg && ins.opcode in ONE_DEF
    is TwoRegisterInstruction -> ins.registerA == reg && ins.opcode in TWO_DEF
    is ThreeRegisterInstruction -> ins.registerA == reg && ins.opcode in THREE_DEF
    else -> false
}

private fun reads(ins: Instruction, reg: Int): Boolean = when (ins) {
    is OneRegisterInstruction ->
        ins.registerA == reg && ins.opcode in ONE_READ

    is TwoRegisterInstruction ->
        ins.registerB == reg ||
            (ins.registerA == reg && ins.opcode !in TWO_DEF)

    is ThreeRegisterInstruction ->
        ins.registerB == reg ||
            ins.registerC == reg ||
            (ins.registerA == reg && ins.opcode !in THREE_DEF)

    is FiveRegisterInstruction ->
        listOf(
            ins.registerC,
            ins.registerD,
            ins.registerE,
            ins.registerF,
            ins.registerG,
        ).take(ins.registerCount).contains(reg)

    is RegisterRangeInstruction ->
        reg in ins.startRegister until (ins.startRegister + ins.registerCount)

    else -> false
}

private fun markOperands(
    ins: Instruction,
    used: BooleanArray,
    localCount: Int,
) {
    fun set(reg: Int) {
        if (reg in 0 until localCount) used[reg] = true
    }

    when (ins) {
        is OneRegisterInstruction -> set(ins.registerA)

        is TwoRegisterInstruction -> {
            set(ins.registerA)
            set(ins.registerB)
        }

        is ThreeRegisterInstruction -> {
            set(ins.registerA)
            set(ins.registerB)
            set(ins.registerC)
        }

        is FiveRegisterInstruction -> {
            listOf(
                ins.registerC,
                ins.registerD,
                ins.registerE,
                ins.registerF,
                ins.registerG,
            ).take(ins.registerCount).forEach { set(it) }
        }

        is RegisterRangeInstruction -> {
            for (reg in ins.startRegister until (ins.startRegister + ins.registerCount)) {
                set(reg)
            }
        }

        else -> Unit
    }
}
