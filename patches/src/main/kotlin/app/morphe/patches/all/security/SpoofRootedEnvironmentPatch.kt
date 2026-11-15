package app.morphe.patches.all.security

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.mutableClassDefByOrNull
import app.morphe.patcher.util.findMutableMethodOf
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private val SU_PATHS = setOf(
    "/system/bin/su",
    "/system/xbin/su",
    "/sbin/su",
    "/system/su",
    "/system/bin/.ext/.su",
    "/system/usr/we-need-root/su-backup",
    "/data/local/su",
    "/data/local/bin/su",
    "/data/local/xbin/su",
    "/magisk/.core/bin/su",
    "/data/adb/magisk/su",
    "/dev/.magisk.unblock",
)

private val ROOT_RE = Regex(
    "^(is|has|check|detect|verify|test|find|look|scan)" +
        ".*(root|su|magisk|kernelsu|supersu|superuser)" +
        ".*(ed|ing|s)?$",
    RegexOption.IGNORE_CASE,
)

private fun forceRootMethods(classDef: ClassDef) {
    val targets = classDef.methods.filter { m ->
        m.implementation != null &&
            m.returnType == "Z" &&
            ROOT_RE.matches(m.name)
    }

    if (targets.isEmpty()) return

    val mc = mutableClassDefBy(classDef)

    targets.forEach { m ->
        mc.findMutableMethodOf(m).returnEarly(true)
    }
}

private fun overrideBuildField(
    mc: MutableClass,
    fieldName: String,
    newValue: String,
) {
    val mm = mc.methods
        .filterIsInstance<MutableMethod>()
        .firstOrNull { it.name == "<clinit>" }
        ?: return
    val sputIdx = mm.instructions.withIndex().firstOrNull { (_, ins) ->
        ins.opcode == Opcode.SPUT_OBJECT &&
            ins.getReference<FieldReference>()?.name == fieldName
    }?.index ?: return
    val sput = mm.getInstruction(sputIdx) as OneRegisterInstruction
    val srcReg = sput.registerA
    for (j in sputIdx - 1 downTo 0) {
        val ins = mm.getInstruction(j)
        if (ins is OneRegisterInstruction && ins.registerA == srcReg) {
            mm.replaceInstruction(j, "const-string v$srcReg, \"$newValue\"")
            return
        }
        val op = ins.opcode
        if (op == Opcode.GOTO || op.name.startsWith("if-") || op.name.startsWith("return")) return
    }
}

private fun spoofBuildFields() {
    val mc = mutableClassDefByOrNull("Landroid/os/Build;") ?: return
    overrideBuildField(mc, "TAGS", "test-keys")
    overrideBuildField(mc, "USER", "root")
    overrideBuildField(mc, "HOST", "buildbot")
}

@Suppress("unused")
val spoofRootedEnvironmentPatch = bytecodePatch(
    name = "Spoof rooted environment",
    description = "Makes Java-level root checks report rooted: forces detection methods " +
        "to return true and overrides Build.TAGS/USER/HOST. For testing bypass patches " +
        "(e.g. PortDroid). Native detection is not affected.",
    default = false,
) {
    val spoofBuild by booleanOption(
        key = "spoofBuild",
        default = true,
        title = "Override Build fields",
        description = "Sets TAGS=test-keys, USER=root, HOST=buildbot in Build.<clinit>.",
    )

    execute {
        classDefForEach(::forceRootMethods)
        if (spoofBuild == true) {
            spoofBuildFields()
        }
    }
}
