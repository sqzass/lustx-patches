/*
 * Copyright 2026 sqzass.
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.postparty.bumper

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.mutableClassDefByOrNull
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags

private const val BUILDER_CLASS = "LV7/a;"

private fun constFor(reg: Int, value: Int): String = when {
    reg < 16 -> "const/4 v$reg, $value"
    reg < 256 -> "const/16 v$reg, $value"
    else -> throw PatchException("Register v$reg is too wide for a const instruction.")
}

/**
 * Nulls an object parameter at method entry. Safe here because the bumper path is
 * nullable by contract and is only read by the bumper condition.
 */
private fun nullOutParam(method: MutableMethod, paramIndex: Int) {
    val impl = method.implementation
        ?: throw PatchException("${method.name} has no implementation.")

    val isStatic = (method.accessFlags and AccessFlags.STATIC.value) != 0
    val paramRegs = method.parameterTypes.sumOf {
        if (it.toString() == "J" || it.toString() == "D") 2 else 1
    } + if (isStatic) 0 else 1

    val reg = impl.registerCount - paramRegs + paramIndex
    method.addInstruction(0, constFor(reg, 0))
}

@Suppress("unused")
val removeClipBumperPatch = patch(
    name = "Remove clip bumper",
    description = "Removes the ~5s Postparty logo bumper from exported clips. " +
        "The bumper is concatenated client-side by FFmpegKit; this nulls the bumper " +
        "path parameter in the export command builder so the trim/scale/concat block " +
        "is never emitted, regardless of whether a bumper file is already cached.",
    default = true,
) {
    compatibleWith(
        Compatibility(
            name = "Postparty",
            packageName = "com.lifeonair.postparty",
            targets = listOf(AppTarget(version = "1.7.0")),
        ),
    )

    execute {
        val mc = mutableClassDefByOrNull(BUILDER_CLASS)
            ?: throw PatchException("$BUILDER_CLASS not found; class name drifted.")

        val target = mc.methods.filterIsInstance<MutableMethod>().firstOrNull { m ->
            m.name == "h" &&
                m.returnType == "Ljava/lang/String;" &&
                m.parameterTypes.size == 8 &&
                (m.accessFlags and AccessFlags.STATIC.value) != 0
        } ?: throw PatchException("V7.a.h(8 args) not found; signature drifted.")

        nullOutParam(target, 4)
    }
}
