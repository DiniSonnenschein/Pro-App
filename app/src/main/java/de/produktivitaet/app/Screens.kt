package de.produktivitaet.app

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(vm: MainViewModel) {
    var pickPlace by remember { mutableStateOf(false) }

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
            )
        }
        vm.homeHint?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = HintRed, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CircleIconButton(Icons.AutoMirrored.Filled.List, "Übersicht", { vm.open(Screen.Overview) })
            CircleIconButton(Icons.Filled.Add, "Aufgabe hinzufügen", { vm.open(Screen.Add) })
        }
    }

    if (pickPlace) {
        SinglePlaceDialog(
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

/** Großer runder Knopf; wird kleiner, wenn wenig Platz ist (z. B. bei offener Tastatur). */
@Composable
private fun BigRedButton(onClick: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .sizeIn(maxWidth = 260.dp, maxHeight = 260.dp)
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .clip(CircleShape)
            .background(DarkRed)
            .border(1.dp, HintRed.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Produktivität",
            fontSize = (maxWidth.value * 0.105f).sp,
            fontWeight = FontWeight.Bold,
            color = White,
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
                modifier = Modifier.fillMaxSize().padding(24.dp),
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
                OutlineButton("Zurück", vm::back, Modifier.fillMaxWidth())
            }
            return@Column
        }

        val step = task.currentStep
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    task.title,
                    fontSize = 30.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (task.totalSteps > 1) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Schritt ${task.completedSteps + 1} von ${task.totalSteps}",
                        color = Muted,
                        fontSize = 16.sp,
                    )
                    if (step.title.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(step.title, fontSize = 22.sp, lineHeight = 30.sp, textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(minutesText(step.minutes), color = Muted, fontSize = 20.sp)
            }
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorButton("Erledigt", LightBlue, Black) { vm.finishStep(task.id) }
                ColorButton("Wiederholen", DarkGreen, White) { vm.backToHome() }
                ColorButton("Verwerfen", DarkRed, White) { vm.finishStep(task.id) }
            }
        }
    }
}

@Composable
fun AddScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Hinzufügen", vm::back)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlineButton(
                    text = "Neue Aufgabe erstellen",
                    onClick = { vm.newTask() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    icon = Icons.Filled.Add,
                )
            }
            item {
                Text(
                    "Vorlagen",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            if (vm.store.templates.isEmpty()) {
                item {
                    Text(
                        "Noch keine Vorlagen. Setze beim Erstellen einer Aufgabe das Häkchen bei " +
                            "„Als Vorlage speichern“, dann erscheint sie hier.",
                        color = Muted,
                        fontSize = 15.sp,
                    )
                }
            }
            items(vm.store.templates, key = { it.id }) { template ->
                TaskCard(template, dimPending = false, onClick = { vm.newTask(template) })
            }
        }
    }
}

@Composable
fun OverviewScreen(vm: MainViewModel) {
    // Paar aus zu löschendem Eintrag und ob es eine Vorlage ist.
    var toDelete by remember { mutableStateOf<Pair<Task, Boolean>?>(null) }
    val showTemplates = vm.overviewTab == 1
    val list = if (showTemplates) vm.store.templates else vm.store.tasks

    Column(Modifier.fillMaxSize()) {
        TopBar("Übersicht", vm::back)
        TabRow(selectedTabIndex = vm.overviewTab, containerColor = Black, contentColor = White) {
            Tab(
                selected = !showTemplates,
                onClick = { vm.overviewTab = 0 },
                text = { Text("Aufgaben (${vm.store.tasks.size})", fontSize = 16.sp) },
                selectedContentColor = White,
                unselectedContentColor = Muted,
            )
            Tab(
                selected = showTemplates,
                onClick = { vm.overviewTab = 1 },
                text = { Text("Vorlagen (${vm.store.templates.size})", fontSize = 16.sp) },
                selectedContentColor = White,
                unselectedContentColor = Muted,
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (list.isEmpty()) {
                item {
                    Text(
                        if (showTemplates) "Noch keine Vorlagen gespeichert." else "Noch keine Aufgaben. Füge über das Plus auf der Startseite welche hinzu.",
                        color = Muted,
                        fontSize = 15.sp,
                    )
                }
            }
            items(list, key = { it.id }) { item ->
                TaskCard(item, dimPending = !showTemplates) {
                    IconButton(onClick = { if (showTemplates) vm.editTemplate(item) else vm.editTask(item) }) {
                        Icon(Icons.Filled.Edit, "Bearbeiten", tint = White)
                    }
                    IconButton(onClick = { toDelete = item to showTemplates }) {
                        Icon(Icons.Filled.Delete, "Löschen", tint = White)
                    }
                }
            }
        }
    }

    toDelete?.let { (item, isTemplate) ->
        ConfirmDeleteDialog(
            title = if (isTemplate) "Vorlage löschen?" else "Aufgabe löschen?",
            text = "„${item.title}“ wird endgültig gelöscht.",
            onConfirm = {
                if (isTemplate) vm.store.deleteTemplate(item.id) else vm.store.deleteTask(item.id)
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
fun EditorScreen(vm: MainViewModel) {
    val editor = vm.editor ?: return
    var placesFor by remember { mutableStateOf<StepDraft?>(null) }
    val screenTitle = when (editor.target) {
        EditTarget.NEW -> "Neue Aufgabe"
        EditTarget.TASK -> "Aufgabe bearbeiten"
        EditTarget.TEMPLATE -> "Vorlage bearbeiten"
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(screenTitle, vm::back)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextInput(editor.title, { editor.title = it }, "Titel", Modifier.fillMaxWidth())

            if (editor.steps.size == 1) {
                StepFields(editor.steps[0], showTitle = false, onPickPlaces = { placesFor = it })
            } else {
                editor.steps.forEachIndexed { i, step ->
                    key(step) {
                        StepCard(
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
            )

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

        Column(Modifier.padding(16.dp)) {
            editor.missingHint()?.let {
                Text(it, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            OutlineButton(
                text = if (editor.target == EditTarget.NEW) "Hinzufügen" else "Speichern",
                onClick = vm::saveEditor,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                enabled = editor.isValid,
            )
        }
    }

    placesFor?.let { step ->
        MultiPlaceDialog(
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
private fun StepCard(number: Int, step: StepDraft, onRemove: () -> Unit, onPickPlaces: (StepDraft) -> Unit) {
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
        StepFields(step, showTitle = true, onPickPlaces = onPickPlaces)
    }
}

@Composable
private fun StepFields(step: StepDraft, showTitle: Boolean, onPickPlaces: (StepDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) {
            TextInput(step.title, { step.title = it }, "Was ist zu tun? (optional)", Modifier.fillMaxWidth())
        }
        SelectField(step.places.label(), "Orte", { onPickPlaces(step) }, Modifier.fillMaxWidth())
        TextInput(
            value = step.minutes,
            onValueChange = { step.minutes = it },
            label = "Dauer",
            numeric = true,
            suffix = "Min.",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
