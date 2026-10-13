package de.produktivitaet.app

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun HomeScreen(vm: MainViewModel) {
    var pickPlace by remember { mutableStateOf(false) }
    var pickPool by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                BigRedButton(onClick = vm::drawTask)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectField(
                    value = vm.homePlace?.label ?: "",
                    label = "Ort",
                    onClick = { pickPlace = true },
                    modifier = Modifier.weight(1f),
                    decoKey = "ort",
                )
                TextInput(
                    value = vm.homeMinutes,
                    onValueChange = {
                        vm.homeMinutes = it
                        vm.homeHint = null
                    },
                    label = "Zeit",
                    numeric = true,
                    suffix = "Min.",
                    modifier = Modifier.weight(1f),
                    decoKey = "zeit",
                )
            }
            vm.homeHint?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = HintRed, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CircleIconButton(Icons.AutoMirrored.Filled.List, "Übersicht", { vm.open(Screen.Overview) }, decoKey = "uebersicht")
                MushroomControls(vm, 64.dp)
                CircleIconButton(Icons.Filled.Add, "Aufgabe hinzufügen", { vm.newTask() }, decoKey = "plus")
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            AppMenu(vm)
        }
        PoolChooser(vm, { pickPool = true }, Modifier.align(Alignment.TopStart).padding(16.dp))
    }

    if (pickPool) {
        PoolPickDialog(
            vm = vm,
            onPick = {
                vm.homePoolId = it.id
                pickPool = false
            },
            onDismiss = { pickPool = false },
        )
    }

    if (pickPlace) {
        SinglePlaceDialog(
            places = vm.store.places,
            selected = vm.homePlace,
            onSelect = {
                vm.homePlace = it
                vm.homeHint = null
                pickPlace = false
            },
            onDismiss = { pickPlace = false },
        )
    }
}

/** Kleines Ordner-Symbol: aktiv (weiß gefüllt) wird nur aus dem gewählten Pool gezogen; nochmal tippen = aus. */
@Composable
private fun PoolChooser(vm: MainViewModel, onOpen: () -> Unit, modifier: Modifier) {
    val pool = vm.homePool
    val bg = if (pool != null) White else areaColor("ordner", Black)
    val fg = onColor(bg)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .decoArea("ordner", CircleShape, Black)
                .clip(CircleShape)
                .background(if (pool != null) White else Color.Transparent)
                .border(1.dp, fg, CircleShape)
                .clickable { if (pool != null) vm.homePoolId = null else onOpen() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_ordner), "Pool wählen", tint = fg, modifier = Modifier.size(22.dp))
        }
        if (pool != null) {
            Spacer(Modifier.width(10.dp))
            Text(pool.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
        }
    }
}

/** Auswahl eines Aufgaben-Pools für die Startseite. */
@Composable
private fun PoolPickDialog(vm: MainViewModel, onPick: (Pool) -> Unit, onDismiss: () -> Unit) {
    val pools = vm.store.pools.filter { !it.forTemplates }.sortedBy { it.sortKey }
    AppDialog("Aus welchem Pool?", onDismiss) {
        if (pools.isEmpty()) {
            Text("Du hast noch keine Aufgaben-Pools. Anlegen kannst du sie in der Übersicht.", color = Muted, fontSize = 16.sp)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pools.forEach { pool ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .border(1.dp, Muted, CardShape)
                        .clickable { onPick(pool) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_ordner), null, tint = White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(pool.name, fontSize = 16.sp, color = White, modifier = Modifier.weight(1f))
                    Text("${vm.store.poolSize(pool)}", fontSize = 14.sp, color = Muted)
                }
            }
        }
    }
}

/** ⋮-Menü: Einstellungen, Statistik, Daten sichern und wiederherstellen. */
@Composable
fun AppMenu(vm: MainViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = vm.exportTo(uri)
            Toast.makeText(context, if (ok) "Daten gesichert." else "Sichern hat nicht geklappt.", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }

    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, "Mehr", tint = LocalContentColor.current)
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.background(Black).border(1.dp, White, CardShape),
        ) {
            DropdownMenuItem(
                text = { Text("Einstellungen", color = White) },
                onClick = {
                    menuOpen = false
                    vm.open(Screen.Settings)
                },
            )
            DropdownMenuItem(
                text = { Text("Statistik", color = White) },
                onClick = {
                    menuOpen = false
                    vm.open(Screen.Statistics)
                },
            )
            DropdownMenuItem(
                text = { Text("Daten sichern", color = White) },
                onClick = {
                    menuOpen = false
                    exportLauncher.launch("like-a-champignon-sicherung-${LocalDate.now()}.json")
                },
            )
            DropdownMenuItem(
                text = { Text("Daten wiederherstellen", color = White) },
                onClick = {
                    menuOpen = false
                    importLauncher.launch(arrayOf("*/*"))
                },
            )
        }
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = "Daten wiederherstellen?",
            text = "Alle aktuellen Aufgaben, Vorlagen, Ziele, Statistik, Pilze und Pilz-Wesen werden durch die Sicherung ersetzt.",
            confirmText = "Ersetzen",
            onConfirm = {
                pendingImport = null
                val ok = vm.importFrom(uri)
                Toast.makeText(
                    context,
                    if (ok) "Daten wiederhergestellt." else "Die Datei ist keine gültige Sicherung.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = { pendingImport = null },
        )
    }
}

/** Großer runder Knopf; wird kleiner, wenn wenig Platz ist (z. B. bei offener Tastatur). */
@Composable
private fun BigRedButton(onClick: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .sizeIn(maxWidth = 260.dp, maxHeight = 260.dp)
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .decoArea("produktivitaet", CircleShape, DarkRed)
            .clip(CircleShape)
            .border(1.dp, HintRed.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Produktivität",
            fontSize = (maxWidth.value * 0.105f).sp,
            fontWeight = FontWeight.Bold,
            color = onColor(areaColor("produktivitaet", DarkRed)),
            maxLines = 1,
        )
    }
}

@Composable
fun DrawScreen(vm: MainViewModel, taskId: Long?) {
    val task = taskId?.let { vm.store.findTask(it) }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (task != null) "Deine Aufgabe" else "Keine Aufgabe", vm::back)

        if (task == null) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Es gibt keine Aufgaben, die du im Moment machen kannst. Entspann dich.",
                    fontSize = 22.sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(40.dp))
                OutlineButton("Zurück", vm::back, Modifier.fillMaxWidth(), decoKey = "zurueck-unten")
            }
            MushroomBar(vm)
            return@Column
        }

        val step = task.currentStep
        val scroll = rememberScrollState()
        RegisterDecorScroll(scroll)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Dieselbe Karte wie in der Übersicht – mit allen Pilzen, die auf der Aufgabe sitzen.
            TaskCard(task, dimPending = true, isTemplate = false, actionSlots = 3) {
                IconButton(onClick = { vm.editTask(task) }) {
                    Icon(Icons.Filled.Edit, "Bearbeiten", tint = LocalContentColor.current)
                }
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.height(28.dp))
            if (task.totalSteps > 1) {
                Text("Schritt ${task.completedSteps + 1} von ${task.totalSteps}", color = Muted, fontSize = 16.sp)
                if (step.title.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(step.title, fontSize = 22.sp, lineHeight = 30.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(14.dp))
            }
            Text(minutesText(step.minutes), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(24.dp))
        }
        Column(
            Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ColorButton("Erledigt", LightBlue, Black, "erledigt") { vm.completeStep(task.id, fromOverview = false) }
            ColorButton("Nicht jetzt", DarkGreen, White, "wiederholen") { vm.backToHome() }
            ColorButton("Verwerfen", DarkRed, White, "verwerfen") { vm.discardTask(task.id) }
        }
        Spacer(Modifier.height(8.dp))
        MushroomBar(vm)
    }
}

@Composable
fun OverviewScreen(vm: MainViewModel) {
    val showTemplates = vm.overviewTab == 1

    Column(Modifier.fillMaxSize()) {
        TopBar("Übersicht", vm::back) {
            AppMenu(vm)
        }
        TabRow(selectedTabIndex = vm.overviewTab, containerColor = Black, contentColor = White) {
            Tab(
                selected = !showTemplates,
                onClick = { vm.overviewTab = 0 },
                text = { Text("Aufgaben (${vm.store.tasks.size})", fontSize = 16.sp) },
                selectedContentColor = onColor(areaColor("reiter-aufgaben", Black)),
                unselectedContentColor = onColor(areaColor("reiter-aufgaben", Black)).copy(alpha = 0.6f),
                modifier = Modifier.decoArea("reiter-aufgaben", RectangleShape, Black),
            )
            Tab(
                selected = showTemplates,
                onClick = { vm.overviewTab = 1 },
                text = { Text("Vorlagen (${vm.store.templates.size})", fontSize = 16.sp) },
                selectedContentColor = onColor(areaColor("reiter-vorlagen", Black)),
                unselectedContentColor = onColor(areaColor("reiter-vorlagen", Black)).copy(alpha = 0.6f),
                modifier = Modifier.decoArea("reiter-vorlagen", RectangleShape, Black),
            )
        }
        key(showTemplates) {
            CardList(vm, forTemplates = showTemplates, poolId = null, modifier = Modifier.weight(1f))
        }
        MushroomBar(vm)
    }
}

@Composable
fun EditorScreen(vm: MainViewModel) {
    val editor = vm.editor ?: return
    var placesFor by remember { mutableStateOf<StepDraft?>(null) }
    val screenTitle = when (editor.target) {
        EditTarget.NEW -> "Neue Aufgabe"
        EditTarget.NEW_TEMPLATE -> "Neue Vorlage"
        EditTarget.TASK -> "Aufgabe bearbeiten"
        EditTarget.TEMPLATE -> "Vorlage bearbeiten"
    }
    val scroll = rememberScrollState()
    RegisterDecorScroll(scroll)
    var pickTemplate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(screenTitle, vm::back)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (editor.target == EditTarget.NEW && vm.store.templates.isNotEmpty()) {
                OutlineButton(
                    text = "Vorlage verwenden",
                    onClick = { pickTemplate = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.AutoMirrored.Filled.List,
                    decoKey = "vorlage-verwenden",
                )
            }
            TextInput(editor.title, { editor.title = it }, "Titel", Modifier.fillMaxWidth(), decoKey = "titel")

            if (editor.steps.size == 1) {
                StepFields(editor.steps[0], 0, showTitle = false, onPickPlaces = { placesFor = it })
            } else {
                editor.steps.forEachIndexed { i, step ->
                    key(step) {
                        StepCard(
                            index = i,
                            number = editor.completedSteps + i + 1,
                            step = step,
                            onRemove = { editor.steps.remove(step) },
                            onPickPlaces = { placesFor = it },
                        )
                    }
                }
            }

            OutlineButton(
                text = "Schritt hinzufügen",
                onClick = { editor.steps.add(StepDraft()) },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.Add,
                decoKey = "schritt-hinzufuegen",
            )

            if (editor.target == EditTarget.NEW_TEMPLATE || editor.target == EditTarget.TEMPLATE) {
                AutoAddSection(editor)
            }

            if (editor.target == EditTarget.NEW) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .clickable { editor.saveAsTemplate = !editor.saveAsTemplate }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = editor.saveAsTemplate,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = White, uncheckedColor = Muted, checkmarkColor = Black),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Als Vorlage speichern", fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            editor.missingHint()?.let {
                Text(it, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            OutlineButton(
                text = if (editor.target == EditTarget.NEW || editor.target == EditTarget.NEW_TEMPLATE) "Hinzufügen" else "Speichern",
                onClick = vm::saveEditor,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                enabled = editor.isValid,
                decoKey = "speichern",
            )
        }
        MushroomBar(vm)
    }

    if (pickTemplate) {
        TemplatePickerDialog(
            templates = vm.store.templates,
            onPick = {
                editor.applyTemplate(it)
                pickTemplate = false
            },
            onDismiss = { pickTemplate = false },
        )
    }

    placesFor?.let { step ->
        MultiPlaceDialog(
            places = vm.store.places,
            selected = step.places,
            onDone = {
                step.places = it
                placesFor = null
            },
            onDismiss = { placesFor = null },
        )
    }
}

@Composable
private fun StepCard(index: Int, number: Int, step: StepDraft, onRemove: () -> Unit, onPickPlaces: (StepDraft) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Muted, CardShape)
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp, top = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Schritt $number", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, "Schritt entfernen", tint = Muted)
            }
        }
        StepFields(step, index, showTitle = true, onPickPlaces = onPickPlaces)
    }
}

@Composable
private fun StepFields(step: StepDraft, index: Int, showTitle: Boolean, onPickPlaces: (StepDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) {
            TextInput(
                step.title,
                { step.title = it },
                "Was ist zu tun? (optional)",
                Modifier.fillMaxWidth(),
                decoKey = "schritt$index-titel",
            )
        }
        SelectField(placesText(step.places), "Orte", { onPickPlaces(step) }, Modifier.fillMaxWidth(), decoKey = "schritt$index-orte")
        TextInput(
            value = step.minutes,
            onValueChange = { step.minutes = it },
            label = "Dauer",
            numeric = true,
            suffix = "Min.",
            modifier = Modifier.fillMaxWidth(),
            decoKey = "schritt$index-dauer",
        )
    }
}

/** Auswahl einer Vorlage im Formular; die gewählte Vorlage überschreibt alle Eingaben. */
@Composable
private fun TemplatePickerDialog(templates: List<Task>, onPick: (Task) -> Unit, onDismiss: () -> Unit) {
    AppDialog("Vorlage wählen", onDismiss) {
        Column(
            Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            templates.forEach { template ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .border(1.dp, Muted, CardShape)
                        .clickable { onPick(template) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(template.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = White)
                    val step = template.currentStep
                    val info = if (template.steps.size > 1) {
                        "${template.steps.size} Schritte"
                    } else {
                        "${placesText(step.places)} · ${step.minutes} Min."
                    }
                    Text(info, fontSize = 13.sp, color = Muted)
                }
            }
        }
    }
}

/** Nur bei Vorlagen: an einem Datum automatisch eine Aufgabe daraus anlegen, optional wiederholt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoAddSection(editor: EditorState) {
    var pickDate by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().border(1.dp, Muted, CardShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .clickable { editor.autoEnabled = !editor.autoEnabled },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = editor.autoEnabled,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = White, uncheckedColor = Muted, checkmarkColor = Black),
            )
            Spacer(Modifier.width(8.dp))
            Text("Automatisch hinzufügen", fontSize = 16.sp)
        }
        if (editor.autoEnabled) {
            SelectField(
                LocalDate.ofEpochDay(editor.autoStart).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                "Datum",
                { pickDate = true },
                Modifier.fillMaxWidth(),
                decoKey = "auto-datum",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Einmalig", selected = editor.repeatKind == 0) { editor.repeatKind = 0 }
                Pill("Alle _ Tage", selected = editor.repeatKind == 1) {
                    if (editor.repeatKind != 1) editor.repeatValue = ""
                    editor.repeatKind = 1
                }
                Pill("Monatlich", selected = editor.repeatKind == 2) {
                    if (editor.repeatKind != 2) editor.repeatValue = ""
                    editor.repeatKind = 2
                }
            }
            when (editor.repeatKind) {
                1 -> TextInput(
                    editor.repeatValue,
                    { editor.repeatValue = it },
                    "Alle wie viele Tage?",
                    Modifier.fillMaxWidth(),
                    numeric = true,
                    suffix = "Tage",
                    decoKey = "auto-tage",
                )
                2 -> TextInput(
                    editor.repeatValue,
                    { editor.repeatValue = it },
                    "An jedem … Tag des Monats",
                    Modifier.fillMaxWidth(),
                    numeric = true,
                    suffix = ". Tag",
                    decoKey = "auto-monatstag",
                )
            }
            Text(
                "Die Aufgabe wird beim Öffnen der App angelegt, sobald der Tag erreicht ist. " +
                    "Ist aus dieser Vorlage noch eine unveränderte Aufgabe offen, setzt der Termin einmal aus.",
                color = Muted,
                fontSize = 13.sp,
            )
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = editor.autoStart * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { editor.autoStart = Math.floorDiv(it, 86_400_000L) }
                    pickDate = false
                }) { Text("OK", color = White) }
            },
            dismissButton = {
                TextButton(onClick = { pickDate = false }) { Text("Abbrechen", color = White) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
