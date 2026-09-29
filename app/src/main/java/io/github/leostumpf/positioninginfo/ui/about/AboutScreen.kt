// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.leostumpf.positioninginfo.ui.common.DataInventory
import io.github.leostumpf.positioninginfo.ui.common.DataOnThisPhone
import io.github.leostumpf.positioninginfo.ui.common.Page
import io.github.leostumpf.positioninginfo.ui.common.PageScaffold
import io.github.leostumpf.positioninginfo.ui.common.section

/** What the app is, its licence and source, and what it keeps on the phone. */
@Composable
fun AboutScreen(dataInventory: DataInventory, onClearAllData: () -> Unit, modifier: Modifier = Modifier) {
    PageScaffold(Page.ABOUT, modifier) {
        section("The app")
        item { AboutSection() }
        section("Data on this phone")
        item { DataOnThisPhone(dataInventory, onClearAllData) }
    }
}
