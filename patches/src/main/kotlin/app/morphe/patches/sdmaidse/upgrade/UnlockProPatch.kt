package app.morphe.patches.sdmaidse.upgrade

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.mutableClassDefBy

private const val UPGRADE_EXTENSIONS = "Leu/darken/sdmse/common/upgrade/UpgradeRepoExtensionsKt;"

@Suppress("unused")
val unlockProPatch = bytecodePatch(
    name = "Unlock Donor Status",
    description = "Unlocks SD Maid SE Pro/Donor features for FOSS build by forcing isPro checks to true",
) {
    compatibleWith("eu.darken.sdmse", "2.2.3-rc0-FOSS")

    execute {
        val clazz = mutableClassDefBy(UPGRADE_EXTENSIONS)
        
        // Patch all isPro-related methods to return Boolean.TRUE
        clazz.methods.filter { method ->
            method.name.startsWith("isPro") && 
            method.returnType == "Ljava/lang/Object;"
        }.forEach { method ->
            // Replace method body to immediately return Boolean.TRUE
            method.addInstructions(0, """
                sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                return-object v0
            """)
        }
    }
}