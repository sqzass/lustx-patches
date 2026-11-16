/*
 * Copyright 2026 sqzass.
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.postparty.bumper

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.findMutableMethodOf
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val CONFIG_CLASS = "Lqj/c;"
private const val BUMPER_FIELD = "p" // enableClipBumper
private const val TRANSCODE_FIELD = "o" // enableClientTranscoding

private fun constFor(reg: Int, value: Int): String = when {
    reg < 16 -> "const/4 v$reg, $value"
    reg < 256 -> "const/16 v$reg, $value"
    else -> "const v$reg, $value"
}

private fun readsField(ins: Instruction, field: String): Boolean = ins.opcode == Opcode.IGET_BOOLEAN &&
    ins.getReference<FieldReference>()?.let {
        it.definingClass == CONFIG_CLASS && it.name == field && it.type == "Z"
    } == true

@Suppress("unused")
val removeClipBumperPatch = patch(
    name = "Remove clip bumper",
    description = "Removes the ~5s Postparty logo bumper appended to exported clips."
    default = true,
) {
    compatibleWith(
        Compatibility(
            name = "Postparty",
            packageName = "com.lifeonair.postparty",
            targets = listOf(AppTarget(version = "1.7.0")),
        ),
    )

    val forceClientTranscoding by booleanOption(
        key = "forceClientTranscoding",
        default = false,
        title = "Force client-side transcoding",
        description = "Also forces enableClientTranscoding true. Only needed if the bumper " +
            "persists after patching (which would mean the server transcodes and adds it). " +
            "Leave off unless required: forcing it on an unsupported device can break export.",
    )

    execute {
        val wantTranscode = forceClientTranscoding == true
        var bumperSites = 0
        var transcodeSites = 0

        classDefForEach { classDef ->
            val candidates = classDef.methods.filter { m ->
                m.implementation?.instructions?.any { ins ->
                    readsField(ins, BUMPER_FIELD) ||
                        (wantTranscode && readsField(ins, TRANSCODE_FIELD))
                } == true
            }
            if (candidates.isEmpty()) return@classDefForEach

            val mc = mutableClassDefBy(classDef)
            candidates.forEach { m ->
                val mm = mc.findMutableMethodOf(m)
                val list = mm.instructions

                // Collect first, patch descending so inserts don't shift later indices.
                val hits = list.indices.filter { i ->
                    readsField(list[i], BUMPER_FIELD) ||
                        (wantTranscode && readsField(list[i], TRANSCODE_FIELD))
                }

                hits.asReversed().forEach { i ->
                    val ins = list[i]
                    val reg = (ins as TwoRegisterInstruction).registerA
                    if (readsField(ins, BUMPER_FIELD)) {
                        mm.addInstruction(i + 1, constFor(reg, 0))
                        bumperSites++
                    } else {
                        mm.addInstruction(i + 1, constFor(reg, 1))
                        transcodeSites++
                    }
                }
            }
        }

        if (bumperSites == 0) {
            throw PatchException(
                "No reads of $CONFIG_CLASS->$BUMPER_FIELD:Z found. The obfuscated field " +
                    "name likely changed in this version; re-check ClientConfigurationResponse.",
            )
        }
    }
}
