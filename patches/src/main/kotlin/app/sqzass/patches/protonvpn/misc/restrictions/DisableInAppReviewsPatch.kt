/* Copyright (C) 2026 sqzass
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.sqzass.patches.protonvpn.misc.restrictions

import app.morphe.patcher.patch.BytecodePatch
import app.morphe.patcher.patch.bytecodePatch
import app.sqzass.patches.protonvpn.misc.settings.patchesSettingsPatch
import app.sqzass.patches.shared.compat.AppCompatibilities

@Suppress("unused")
val disableInAppReviewsPatch: BytecodePatch = bytecodePatch(
    name = "Disable in-app reviews",
    description = "Disables in-app review prompts.",
) {
    compatibleWith(AppCompatibilities.PROTON_VPN)
    dependsOn(patchesSettingsPatch)

    execute {
        // Find the ReviewPromptHandler class
        val clazz = app.morphe.patcher.patch.classDefBy("Lcom/protonvpn/android/redesign/app/ui/review/ReviewPromptHandler;")
        // Find the showReviewPrompt method
        val showMethod = clazz.methods.singleOrNull { it.name == "showReviewPrompt" && it.parameterTypes.size == 0 }
            ?: throw IllegalStateException("Review prompt method not found")
        // Replace the method body to return void
        showMethod.returnEarly(0)
    }
}
