package de.produktivitaet.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val Muted = Color(0xFF9A9A9A)
val Dim = Color(0xFF555555)
val DarkRed = Color(0xFF8E1B1B)
val LightBlue = Color(0xFF8FD3F4)
val DarkGreen = Color(0xFF1F6B3A)
val HintRed = Color(0xFFE88A8A)

private val FieldShape = RoundedCornerShape(12.dp)
val CardShape = RoundedCornerShape(14.dp)

@Composable
fun ProduktivitaetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = White,
            onPrimary = Black,
            background = Black,
            onBackground = White,
            surface = Black,
            onSurface = White,
            surfaceVariant = Black,
            onSurfaceVariant = Muted,
            outline = White,
            outlineVariant = Color(0xFF2A2A2A),
        ),
        content = content,
    )
}

/** Standard-Schalter: schwarz mit dünner weißer Umrandung. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = FieldShape,
        border = BorderStroke(1.dp, if (enabled) White else Dim),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Black,
            contentColor = White,
            disabledContainerColor = Black,
            disabledContentColor = Dim,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 16.sp)
    }
}

/** Farbige Schalter im Aufgabenfenster. */
@Composable
fun ColorButton(text: String, color: Color, textColor: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = CardShape,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = textColor),
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 64.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Black)
            .border(1.dp, White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = White, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", onBack, size = 44.dp)
        Spacer(Modifier.width(16.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        actions()
    }
}

/** Pilz-Button: öffnet das Fungarium. */
@Composable
fun MushroomButton(onClick: () -> Unit, size: Dp = 64.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Black)
            .border(1.dp, White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_pilz), "Fungarium", tint = White, modifier = Modifier.size(size * 0.5f))
    }
}

/** Leiste unten in der Mitte mit dem Pilz-Button – für alle Fenster außer der Startseite. */
@Composable
fun MushroomBar(vm: MainViewModel) {
    Box(Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 4.dp), contentAlignment = Alignment.Center) {
        MushroomButton(onClick = { vm.open(Screen.Fungarium) }, size = 56.dp)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(CircleShape)
            .background(Color(0xFF262626)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Color(0xFF8FD3F4), Color(0xFFB6F09C)))),
        )
    }
}

val SilhouetteColor = Color(0xFF3A3A3A)

/** Pilzbild aus dem Katalog; lädt im Hintergrund. Als Silhouette einfarbig dunkel. */
@Composable
fun MushroomImage(
    catalog: MushroomCatalog,
    species: String,
    modifier: Modifier = Modifier,
    silhouette: Boolean = false,
    maxPx: Int = 400,
) {
    val bitmap by produceState(catalog.cached(species, maxPx), species, maxPx) {
        if (value == null) value = withContext(Dispatchers.IO) { catalog.load(species, maxPx) }
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image == null) {
        Box(modifier)
    } else {
        Image(
            bitmap = image,
            contentDescription = catalog.find(species)?.name,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            colorFilter = if (silhouette) ColorFilter.tint(SilhouetteColor, BlendMode.SrcIn) else null,
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White,
    unfocusedTextColor = White,
    focusedContainerColor = Black,
    unfocusedContainerColor = Black,
    focusedBorderColor = White,
    unfocusedBorderColor = Muted,
    focusedLabelColor = White,
    unfocusedLabelColor = Muted,
    cursorColor = White,
    focusedSuffixColor = Muted,
    unfocusedSuffixColor = Muted,
    focusedTrailingIconColor = White,
    unfocusedTrailingIconColor = Muted,
)

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(if (numeric) it.filter(Char::isDigit).take(4) else it) },
        label = { Text(label) },
        suffix = if (suffix != null) { { Text(suffix) } } else null,
        singleLine = true,
        shape = FieldShape,
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(
            capitalization = if (numeric) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = ImeAction.Done,
        ),
        modifier = modifier,
    )
}

/** Sieht aus wie ein Eingabefeld, öffnet beim Antippen aber eine Auswahl. */
@Composable
fun SelectField(value: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            shape = FieldShape,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
fun AppDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(Black)
                .border(1.dp, White, CardShape)
                .padding(20.dp),
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = White)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ChoiceRow(onClick: () -> Unit, control: @Composable () -> Unit, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        control()
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 17.sp, color = White)
    }
}

/** Startseite: genau ein Ort. */
@Composable
fun SinglePlaceDialog(selected: Place?, onSelect: (Place) -> Unit, onDismiss: () -> Unit) {
    AppDialog("Wo bist du?", onDismiss) {
        Place.entries.forEach { place ->
            ChoiceRow(
                onClick = { onSelect(place) },
                control = {
                    RadioButton(
                        selected = place == selected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = White, unselectedColor = Muted),
                    )
                },
                text = place.label,
            )
        }
    }
}

/** Aufgabe: ein oder mehrere Orte; "Überall" (mit Abstand darunter) schließt alle ein. */
@Composable
fun MultiPlaceDialog(selected: Places, onDone: (Places) -> Unit, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf(selected) }
    val checkboxColors = CheckboxDefaults.colors(
        checkedColor = White,
        uncheckedColor = Muted,
        checkmarkColor = Black,
    )
    AppDialog("Orte", onDismiss) {
        Place.entries.forEach { place ->
            ChoiceRow(
                onClick = { current = current.toggle(place) },
                control = { Checkbox(checked = place in current, onCheckedChange = null, colors = checkboxColors) },
                text = place.label,
            )
        }
        Spacer(Modifier.height(14.dp))
        ChoiceRow(
            onClick = { current = current.toggleEverywhere() },
            control = { Checkbox(checked = current.everywhere, onCheckedChange = null, colors = checkboxColors) },
            text = "Überall",
        )
        Spacer(Modifier.height(12.dp))
        OutlineButton("Fertig", { onDone(current) }, Modifier.fillMaxWidth())
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmColor: Color = DarkRed,
    confirmTextColor: Color = White,
) {
    AppDialog(title, onDismiss) {
        Text(text, color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineButton("Abbrechen", onDismiss, Modifier.weight(1f))
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                shape = FieldShape,
                colors = ButtonDefaults.buttonColors(containerColor = confirmColor, contentColor = confirmTextColor),
            ) { Text(confirmText, fontSize = 16.sp) }
        }
    }
}

private fun stepInfo(step: Step) = "${step.places.label} · ${step.minutes} Min."

/**
 * Karte für eine Aufgabe oder Vorlage. Bei mehreren Schritten werden alle aufgelistet;
 * mit [dimPending] sind die noch nicht aktiven Schritte ausgegraut.
 */
@Composable
fun TaskCard(
    task: Task,
    dimPending: Boolean,
    onClick: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, White, CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                task.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            actions?.invoke(this)
        }
        if (task.totalSteps == 1) {
            Text(stepInfo(task.currentStep), color = Muted, fontSize = 14.sp)
        } else {
            task.steps.forEachIndexed { i, step ->
                val number = task.completedSteps + i + 1
                val active = !dimPending || i == 0
                Row(Modifier.padding(top = 6.dp, end = 12.dp)) {
                    Text("$number.", color = if (active) White else Dim, fontSize = 15.sp, modifier = Modifier.width(28.dp))
                    Column {
                        Text(step.title.ifBlank { "Schritt $number" }, color = if (active) White else Dim, fontSize = 15.sp)
                        Text(stepInfo(step), color = if (active) Muted else Dim, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
