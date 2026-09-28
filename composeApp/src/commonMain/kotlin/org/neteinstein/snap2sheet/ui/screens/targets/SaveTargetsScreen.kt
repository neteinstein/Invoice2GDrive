package org.neteinstein.snap2sheet.ui.screens.targets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.neteinstein.snap2sheet.domain.model.AppendRules
import org.neteinstein.snap2sheet.domain.model.DriveItem
import org.neteinstein.snap2sheet.ui.components.FaturaCard
import org.neteinstein.snap2sheet.ui.components.FaturaDashedButton
import org.neteinstein.snap2sheet.ui.components.FaturaIcons
import org.neteinstein.snap2sheet.ui.components.FaturaPrimaryButton
import org.neteinstein.snap2sheet.ui.components.FaturaTopBar
import org.neteinstein.snap2sheet.ui.components.IconBadge
import org.neteinstein.snap2sheet.ui.components.Notice
import org.neteinstein.snap2sheet.ui.components.SectionTitle
import org.neteinstein.snap2sheet.ui.components.TextInputDialog
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

@Composable
fun SaveTargetsScreen(
    mode: SaveTargetsMode,
    onBack: (() -> Unit)?,
    onDone: () -> Unit,
    viewModel: SaveTargetsViewModel = koinViewModel(key = mode.name) { parametersOf(mode) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.done) { if (state.done) onDone() }

    Column(modifier = Modifier.fillMaxSize().background(FaturaColors.Surface)) {
        FaturaTopBar(
            title = when (mode) {
                SaveTargetsMode.SETUP -> "Where should invoices go?"
                SaveTargetsMode.SAVE -> "Save this invoice to"
                SaveTargetsMode.DEFAULTS -> "Defaults"
            },
            onBack = onBack,
            trailing = {
                Text(
                    if (state.isRefreshing) "Refreshing…" else "Refresh",
                    style = MaterialTheme.typography.labelMedium,
                    color = FaturaColors.Accent,
                    modifier = if (state.isRefreshing) Modifier else Modifier.clickable(onClick = viewModel::refresh),
                )
            },
        )

        if (mode == SaveTargetsMode.SETUP) {
            Text(
                "Each invoice becomes a row in a spreadsheet, and its photo goes into a Drive folder. " +
                    "Pick the defaults now — you can change them per invoice or in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = FaturaColors.Muted,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
            )
        }

        TargetSummary(
            spreadsheet = state.spreadsheet,
            folder = state.folder,
            tab = state.tab,
            onSelectTab = viewModel::selectTab,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp)
                .background(FaturaColors.Background, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FaturaIcons.Search(tint = FaturaColors.MutedStrong, size = 16.dp)
            Box(modifier = Modifier.weight(1f)) {
                if (state.query.isEmpty()) {
                    Text(
                        if (state.tab == TargetKind.SPREADSHEET) "Search your Google Sheets" else "Search your Drive folders",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FaturaColors.Subtle,
                    )
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    singleLine = true,
                    cursorBrush = SolidColor(FaturaColors.Accent),
                    textStyle = TextStyle(color = FaturaColors.Ink, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            state.error?.let { error ->
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp)) {
                        Notice(
                            text = "$error  Tap to dismiss.",
                            color = FaturaColors.Danger,
                            background = FaturaColors.DangerSoft,
                            modifier = Modifier.clickable(onClick = viewModel::dismissError),
                        )
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
                    SectionTitle(text = if (state.tab == TargetKind.SPREADSHEET) "Your spreadsheets" else "Your folders")
                }
            }

            if (state.filtered.isEmpty()) {
                item {
                    Text(
                        when {
                            state.isRefreshing -> "Loading…"
                            state.query.isNotBlank() -> "Nothing matches \"${state.query}\"."
                            else -> "Nothing here yet. Create one below."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = FaturaColors.Muted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }

            items(state.filtered, key = { state.tab.name + it.item.id }) { row ->
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)) {
                    OptionRow(
                        item = row.item,
                        subtitle = row.subtitle,
                        kind = state.tab,
                        selected = row.item.id == state.selectedId,
                        onClick = { viewModel.select(row.item) },
                    )
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    FaturaDashedButton(
                        text = when {
                            state.isCreating -> "Creating…"
                            state.tab == TargetKind.SPREADSHEET -> "Create new spreadsheet"
                            else -> "Create new folder"
                        },
                        onClick = { if (!state.isCreating) showCreateDialog = true },
                    ) {
                        FaturaIcons.Plus(tint = FaturaColors.Muted)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    if (mode == SaveTargetsMode.SAVE) {
                        ToggleCard(
                            title = "Make these my defaults",
                            subtitle = "Otherwise they're used for this invoice only.",
                            checked = state.makeDefault,
                            onCheckedChange = viewModel::onMakeDefaultChange,
                        )
                    } else {
                        ToggleCard(
                            title = "Always save to my defaults",
                            subtitle = "Skip this choice after reviewing an invoice. You can change this anytime in Settings.",
                            checked = state.alwaysSaveToDefaults,
                            onCheckedChange = viewModel::onAlwaysSaveChange,
                        )
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
                    FaturaCard(modifier = Modifier.fillMaxWidth()) {
                        Text("APPEND RULES", style = MaterialTheme.typography.labelSmall, color = FaturaColors.MutedStrong)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            appendRulesSummary(state.appendRules),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FaturaColors.Ink,
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(20.dp)) {
            FaturaPrimaryButton(
                text = when (mode) {
                    SaveTargetsMode.SETUP -> "Start scanning"
                    SaveTargetsMode.SAVE -> "Save & Continue"
                    SaveTargetsMode.DEFAULTS -> "Done"
                },
                onClick = viewModel::confirm,
                enabled = state.canContinue,
            )
        }
    }

    if (showCreateDialog) {
        TextInputDialog(
            title = if (state.tab == TargetKind.SPREADSHEET) "New spreadsheet" else "New Drive folder",
            label = "Name",
            confirmText = "Create",
            initialValue = remember(state.tab) { viewModel.suggestedName() },
            supportingText = if (state.tab == TargetKind.SPREADSHEET) "Created in your Google Drive with Fatura's columns." else "Created at the top of your Google Drive.",
            onConfirm = {
                showCreateDialog = false
                viewModel.create(it)
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

/** The two choices side by side; tapping one switches the list below to it. */
@Composable
private fun TargetSummary(spreadsheet: DriveItem?, folder: DriveItem?, tab: TargetKind, onSelectTab: (TargetKind) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(TargetKind.SPREADSHEET to spreadsheet, TargetKind.FOLDER to folder).forEach { (kind, item) ->
            val active = kind == tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) FaturaColors.AccentSoft else FaturaColors.SurfaceMuted)
                    .border(1.5.dp, if (active) FaturaColors.Accent else FaturaColors.Border, RoundedCornerShape(14.dp))
                    .clickable { onSelectTab(kind) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (kind == TargetKind.SPREADSHEET) FaturaIcons.Document(tint = FaturaColors.Accent, size = 14.dp)
                    else FaturaIcons.Folder(tint = FaturaColors.Accent, size = 14.dp)
                    Text(kind.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = FaturaColors.MutedStrong)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    item?.name ?: "Choose…",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (item != null) FaturaColors.Ink else FaturaColors.Subtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ToggleCard(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    FaturaCard(containerColor = FaturaColors.SurfaceMuted, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = FaturaColors.Ink, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = FaturaColors.Accent))
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FaturaColors.Muted)
    }
}

private fun appendRulesSummary(rules: AppendRules): String = listOfNotNull(
    "New row per invoice",
    if (rules.matchColumnsByHeader) "Columns matched by header" else "Fixed column order",
    if (rules.skipDuplicateInvoices) "Skip duplicate ATCUD" else null,
    if (rules.newSheetTabEachMonth) "One tab per month" else null,
    "Photo linked in the row",
).joinToString(" · ")

@Composable
private fun OptionRow(item: DriveItem, subtitle: String, kind: TargetKind, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) FaturaColors.AccentSoft else FaturaColors.Surface)
            .border(1.5.dp, if (selected) FaturaColors.Accent else FaturaColors.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(size = 36.dp, background = FaturaColors.Surface) {
            val tint = if (selected) FaturaColors.Accent else FaturaColors.Muted
            if (kind == TargetKind.SPREADSHEET) FaturaIcons.Document(tint = tint, size = 18.dp) else FaturaIcons.Folder(tint = tint, size = 18.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = FaturaColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FaturaColors.MutedStrong)
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) FaturaColors.Accent else FaturaColors.BorderStrong, CircleShape)
                .background(FaturaColors.Surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(FaturaColors.Accent))
        }
    }
}
