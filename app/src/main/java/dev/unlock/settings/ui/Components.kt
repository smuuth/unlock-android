package dev.unlock.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal val CardShape = RoundedCornerShape(28.dp)

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, top = 16.dp),
    )
}

/** Eine Zeile mit Schalter für [SwitchList]. */
internal data class SwitchRow(
    val title: Int,
    val description: Int,
    val checked: Boolean,
    val onChange: (Boolean) -> Unit,
    val enabled: Boolean = true,
)

/** Zusammenhängende Schalter-Liste (Expressive Segmented List). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SwitchList(rows: List<SwitchRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rows.forEachIndexed { index, row ->
            SegmentedListItem(
                checked = row.checked,
                onCheckedChange = row.onChange,
                enabled = row.enabled,
                shapes = ListItemDefaults.segmentedShapes(index, rows.size),
                colors = calmSegmentedColors(),
                supportingContent = { Text(stringResource(row.description)) },
                trailingContent = { Switch(checked = row.checked, onCheckedChange = null, enabled = row.enabled) },
            ) {
                Text(stringResource(row.title))
            }
        }
    }
}

/** Connected Button Group (Expressive): Segmente, deren Ecken beim Auswählen/Drücken morphen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ConnectedToggleRow(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth(),
    ) {
        labels.forEachIndexed { index, label ->
            ToggleButton(
                checked = index == selectedIndex,
                onCheckedChange = { onSelect(index) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = segmentColors(),
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton },
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Ausgewählt: kräftiges Primary; sonst ruhiges Secondary-Container – klar unterscheidbar in Hell & Dunkel. */
@Composable
internal fun segmentColors() = ToggleButtonDefaults.colors(
    containerColor = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    checkedContainerColor = MaterialTheme.colorScheme.primary,
    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
)

/** Einheitlicher Hintergrund – der Zustand wird vom Switch angezeigt, nicht von der ganzen Zeile. */
@Composable
internal fun calmSegmentedColors() = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    selectedContentColor = MaterialTheme.colorScheme.onSurface,
    selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)
