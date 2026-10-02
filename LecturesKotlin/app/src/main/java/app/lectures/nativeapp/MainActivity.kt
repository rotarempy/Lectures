package app.lectures.nativeapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var store: DataStore
    private lateinit var sync: LanSync
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = DataStore(this)
        sync = LanSync(this, store).also { it.start() }
        setContent { LecturesApp(store, sync) }
    }
    override fun onDestroy() { sync.stop(); super.onDestroy() }
}

private enum class Page(val title: String) {
    WEEK("Неделя"), SUBJECTS("Предметы"), HOMEWORK("ДЗ"), MANAGE("Список")
}

private val Ink = Color(0xFF111019)
private val Panel = Color(0xFF181623)
private val Panel2 = Color(0xFF211E31)
private val Sage = Color(0xFF6556F4)
private val Muted = Color(0xFF9A96AD)
private val Border = Color(0xFF302C45)
private val AppText = Color(0xFFF3F1FB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LecturesApp(store: DataStore, sync: LanSync) {
    val data by store.data.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var page by remember { mutableStateOf(Page.WEEK) }
    var week by remember { mutableStateOf(LocalDate.now().with(DayOfWeek.MONDAY)) }
    var overflow by remember { mutableStateOf(false) }
    var syncDialog by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var editLecture by remember { mutableStateOf<Lecture?>(null) }
    var newLecture by remember { mutableStateOf(false) }
    var editHomework by remember { mutableStateOf<Homework?>(null) }
    var newHomework by remember { mutableStateOf(false) }
    var newSubject by remember { mutableStateOf(false) }
    var editSubject by remember { mutableStateOf<Subject?>(null) }
    var lectureDraftDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var attachmentCallback by remember { mutableStateOf<((List<Attachment>) -> Unit)?>(null) }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.exportJson()) } ?: error("Не удалось создать файл") } }
                .onSuccess { snackbar.showSnackbar("Резервная копия сохранена") }
                .onFailure { snackbar.showSnackbar("Ошибка экспорта: ${it.message}") }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val raw = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Не удалось прочитать файл") }
                parseAppData(raw)
                raw
            }.onSuccess { pendingImport = it }
                .onFailure { snackbar.showSnackbar("Ошибка импорта: ${it.message}") }
        }
    }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val callback = attachmentCallback
        if (callback != null && uris.isNotEmpty()) scope.launch {
            val files = withContext(Dispatchers.IO) { uris.mapNotNull { it.toAttachment(context) } }
            callback(files)
            attachmentCallback = null
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Sage,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF312B59),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFFAAA4C7),
        onSecondary = Ink,
        secondaryContainer = Panel2,
        onSecondaryContainer = AppText,
        background = Ink,
        surface = Panel,
        surfaceVariant = Panel2,
        onBackground = AppText,
        onSurface = AppText,
        onSurfaceVariant = Muted,
        outline = Border,
        error = Color(0xFFC95A70),
        tertiary = Color(0xFF5F9875)
    )) {
        Scaffold(
            containerColor = Ink,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Лекции и ДЗ", fontWeight = FontWeight.SemiBold)
                            if (page == Page.WEEK) Text(week.format(DateTimeFormatter.ofPattern("d MMM")) + " — " + week.plusDays(5).format(DateTimeFormatter.ofPattern("d MMM")), style = MaterialTheme.typography.labelMedium, color = Muted)
                        }
                    },
                    actions = {
                        if (page == Page.WEEK) {
                            IconButton(onClick = { week = week.minusWeeks(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Предыдущая неделя") }
                            IconButton(onClick = { week = LocalDate.now().with(DayOfWeek.MONDAY) }) { Icon(Icons.Default.CalendarMonth, "Текущая неделя") }
                            IconButton(onClick = { week = week.plusWeeks(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Следующая неделя") }
                        }
                        Box {
                            IconButton(onClick = { overflow = true }) { Icon(Icons.Default.MoreVert, "Меню") }
                            DropdownMenu(
                                expanded = overflow,
                                onDismissRequest = { overflow = false },
                                modifier = Modifier.width(310.dp),
                                shape = RoundedCornerShape(18.dp),
                                containerColor = Panel,
                                tonalElevation = 0.dp,
                                shadowElevation = 16.dp
                            ) {
                                Text(
                                    "Дополнительно",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppText,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                                )
                                HorizontalDivider(color = Border)
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Перенос между устройствами", color = AppText)
                                            Text("Передача по локальной Wi‑Fi сети", style = MaterialTheme.typography.labelSmall, color = Muted)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Sync, null, tint = Sage) },
                                    onClick = { overflow = false; syncDialog = true },
                                    colors = MenuDefaults.itemColors(textColor = AppText, leadingIconColor = Sage)
                                )
                                HorizontalDivider(color = Border, modifier = Modifier.padding(horizontal = 12.dp))
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Импорт резервной копии", color = AppText)
                                            Text("Загрузить данные из JSON-файла", style = MaterialTheme.typography.labelSmall, color = Muted)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.FileUpload, null, tint = Sage) },
                                    onClick = { overflow = false; importPicker.launch(arrayOf("application/json", "text/*", "*/*")) },
                                    colors = MenuDefaults.itemColors(textColor = AppText, leadingIconColor = Sage)
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Экспорт резервной копии", color = AppText)
                                            Text("Сохранить данные в JSON-файл", style = MaterialTheme.typography.labelSmall, color = Muted)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.FileDownload, null, tint = Sage) },
                                    onClick = { overflow = false; exportPicker.launch("lectures-backup-${LocalDate.now()}.json") },
                                    colors = MenuDefaults.itemColors(textColor = AppText, leadingIconColor = Sage)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    Page.entries.forEach { destination ->
                        val icon = when (destination) {
                            Page.WEEK -> Icons.Default.CalendarMonth
                            Page.SUBJECTS -> Icons.Default.MenuBook
                            Page.HOMEWORK -> Icons.Default.Checklist
                            Page.MANAGE -> Icons.Default.School
                        }
                        NavigationBarItem(selected = page == destination, onClick = { page = destination }, icon = { Icon(icon, null) }, label = { Text(destination.title) })
                    }
                }
            },
        ) { padding ->
            when (page) {
                Page.WEEK -> WeekPage(data, week, padding, onAdd = { selectedDate -> lectureDraftDate = selectedDate.toString(); newLecture = true }) { editLecture = it }
                Page.SUBJECTS -> SubjectsPage(data, padding) { editLecture = it }
                Page.HOMEWORK -> HomeworkPage(data, padding, onAdd = { newHomework = true }, onToggle = { hw ->
                    scope.launch { store.update { db -> db.copy(homeworks = db.homeworks.map { if (it.id == hw.id) it.copy(done = !it.done) else it }) } }
                }) { editHomework = it }
                Page.MANAGE -> ManagePage(data, padding, onAdd = { newSubject = true }, onEdit = { editSubject = it }, onDelete = { subject ->
                    scope.launch { store.update { db ->
                        db.copy(subjects = db.subjects.filterNot { it.id == subject.id },
                            lectures = db.lectures.map { if (it.subjectId == subject.id) it.copy(subjectId = null) else it },
                            homeworks = db.homeworks.map { if (it.subjectId == subject.id) it.copy(subjectId = null) else it })
                    } }
                })
            }
        }

        if (newLecture || editLecture != null) {
            val initial = editLecture ?: Lecture(date = lectureDraftDate)
            LectureEditor(initial, data.subjects, onDismiss = { newLecture = false; editLecture = null },
                onAttach = { callback -> attachmentCallback = callback; attachmentPicker.launch(arrayOf("image/*", "application/pdf", "text/plain")) },
                onOpenAttachment = { openAttachment(context, it) },
                onDelete = if (editLecture != null) ({
                    scope.launch { store.update { db -> db.copy(lectures = db.lectures.filterNot { it.id == initial.id }) } }
                    newLecture = false; editLecture = null
                }) else null,
                onSave = { changed -> scope.launch {
                    store.update { db ->
                        if (db.lectures.any { it.id == changed.id }) db.copy(lectures = db.lectures.map { if (it.id == changed.id) changed else it })
                        else db.copy(lectures = db.lectures + changed)
                    }
                    newLecture = false; editLecture = null
                } })
        }
        if (newHomework || editHomework != null) {
            val initial = editHomework ?: Homework(addedDate = LocalDate.now().toString())
            HomeworkEditor(initial, data.subjects, onDismiss = { newHomework = false; editHomework = null },
                onDelete = if (editHomework != null) ({ scope.launch { store.update { db -> db.copy(homeworks = db.homeworks.filterNot { it.id == initial.id }) } }; newHomework = false; editHomework = null }) else null,
                onSave = { changed -> scope.launch {
                    store.update { db -> if (db.homeworks.any { it.id == changed.id }) db.copy(homeworks = db.homeworks.map { if (it.id == changed.id) changed else it }) else db.copy(homeworks = db.homeworks + changed) }
                    newHomework = false; editHomework = null
                } })
        }
        if (newSubject || editSubject != null) {
            val initial = editSubject
            SubjectEditor(initial, onDismiss = { newSubject = false; editSubject = null }, onSave = { name -> scope.launch {
                store.update { db -> if (initial == null) db.copy(subjects = db.subjects + Subject(name = name)) else db.copy(subjects = db.subjects.map { if (it.id == initial.id) it.copy(name = name) else it }) }
                newSubject = false; editSubject = null
            } })
        }
        if (syncDialog) SyncDialog(sync, store, onDismiss = { syncDialog = false }, onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } })
        pendingImport?.let { raw ->
            val incoming = remember(raw) { runCatching { parseAppData(raw) }.getOrDefault(AppData()) }
            AlertDialog(
                onDismissRequest = { pendingImport = null },
                title = { Text("Заменить текущие данные?") },
                text = { Text("В файле: ${incoming.subjects.size} предметов, ${incoming.lectures.size} лекций, ${incoming.homeworks.size} заданий. Перед импортом приложение сохранит резервную копию текущей базы.") },
                confirmButton = { TextButton(onClick = {
                    pendingImport = null
                    scope.launch { runCatching { store.replaceFromJson(raw) }
                        .onSuccess { snackbar.showSnackbar("Резервная копия импортирована") }
                        .onFailure { snackbar.showSnackbar("Ошибка импорта: ${it.message}") } }
                }) { Text("Импортировать") } },
                dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Отмена") } }
            )
        }
    }
}

@Composable
private fun WeekPage(data: AppData, monday: LocalDate, padding: PaddingValues, onAdd: (LocalDate) -> Unit, onEdit: (Lecture) -> Unit) {
    val days = (0..5).map { monday.plusDays(it.toLong()) }
    val today = LocalDate.now()
    val currentMonday = today.with(DayOfWeek.MONDAY)
    val initialDayIndex = if (monday == currentMonday) (today.dayOfWeek.value - 1).coerceIn(0, 5) else 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialDayIndex)
    LazyRow(Modifier.fillMaxSize().padding(padding), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(days) { day ->
            val lectures = data.lectures.filter { it.date == day.toString() }.sortedBy { it.title.lowercase() }
            Surface(Modifier.width(290.dp).fillMaxHeight(), color = Panel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .035f))) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(day.format(DateTimeFormatter.ofPattern("EEEE", java.util.Locale("ru"))).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
                            Text(day.format(DateTimeFormatter.ofPattern("d MMMM", java.util.Locale("ru"))), style = MaterialTheme.typography.labelMedium, color = Muted)
                        }
                        IconButton(onClick = { onAdd(day) }) { Icon(Icons.Default.Add, "Добавить лекцию") }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = .06f))
                    if (lectures.isEmpty()) Text("Пока нет лекций", color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
                    else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Spacer(Modifier.height(3.dp))
                        lectures.forEach { lecture -> LectureCard(lecture, data.subjects) { onEdit(lecture) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun LectureCard(lecture: Lecture, subjects: List<Subject>, onClick: () -> Unit) {
    val subject = subjects.find { it.id == lecture.subjectId }
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), color = Panel2, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(lecture.title.ifBlank { "Лекция" }, fontWeight = FontWeight.SemiBold)
            if (subject != null) Text(subject.name, color = Sage, style = MaterialTheme.typography.labelMedium)
            if (lecture.content.isNotBlank()) Text(lecture.content.take(180), maxLines = 4, color = Muted, style = MaterialTheme.typography.bodySmall)
            if (lecture.attachments.isNotEmpty()) Text("📎 ${lecture.attachments.size} вложений", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SubjectsPage(data: AppData, padding: PaddingValues, onEdit: (Lecture) -> Unit) {
    if (data.subjects.isEmpty()) EmptyState("Пока нет предметов", "Добавьте предмет в разделе «Список».", padding)
    else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(data.subjects, key = { it.id }) { subject ->
            val lectures = data.lectures.filter { it.subjectId == subject.id }.sortedBy { it.date }
            Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(subject.name, style = MaterialTheme.typography.titleMedium, color = Sage, fontWeight = FontWeight.SemiBold)
                    if (lectures.isEmpty()) Text("Лекций пока нет", color = Muted, style = MaterialTheme.typography.bodySmall)
                    lectures.forEach { lecture -> LectureCard(lecture, data.subjects, onClick = { onEdit(lecture) }) }
                }
            }
        }
    }
}

@Composable
private fun HomeworkPage(data: AppData, padding: PaddingValues, onAdd: () -> Unit, onToggle: (Homework) -> Unit, onEdit: (Homework) -> Unit) {
    val sorted = data.homeworks.sortedWith(compareBy<Homework> { it.done }.thenBy { it.dueDate.ifBlank { "9999" } }.thenByDescending { it.addedDate })
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Домашние задания", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onAdd) { Text("Добавить ДЗ") }
        }
        if (sorted.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Домашних заданий пока нет", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp)); Text("Добавьте первое задание кнопкой выше.", color = Muted)
            }
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(sorted, key = { it.id }) { hw ->
            val subject = data.subjects.find { it.id == hw.subjectId }
            Surface(Modifier.fillMaxWidth().clickable { onEdit(hw) }, color = Panel, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hw.done, onCheckedChange = { onToggle(hw) })
                    Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(hw.text.ifBlank { "Домашнее задание" }, fontWeight = FontWeight.Medium, color = if (hw.done) Muted else MaterialTheme.colorScheme.onSurface)
                        Text(listOfNotNull(subject?.name, hw.dueDate.takeIf(String::isNotBlank)?.let { "Срок: $it" }).joinToString(" · ").ifBlank { "Добавлено ${hw.addedDate}" }, style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ManagePage(data: AppData, padding: PaddingValues, onAdd: () -> Unit, onEdit: (Subject) -> Unit, onDelete: (Subject) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Предметы", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onAdd) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Добавить") }
        }
        if (data.subjects.isEmpty()) Text("Добавьте предмет, чтобы связывать его с лекциями и ДЗ.", color = Muted, modifier = Modifier.padding(top = 20.dp))
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(data.subjects, key = { it.id }) { subject ->
                Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(subject.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        IconButton(onClick = { onEdit(subject) }) { Icon(Icons.Default.Edit, "Переименовать") }
                        IconButton(onClick = { onDelete(subject) }) { Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, details: String, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp)); Text(details, color = Muted)
    }
}

@Composable
private fun SubjectPicker(value: String?, subjects: List<Subject>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = subjects.find { it.id == value }?.name ?: "Без предмета"
    Box {
        OutlinedTextField(value = current, onValueChange = {}, readOnly = true, label = { Text("Предмет") }, modifier = Modifier.fillMaxWidth())
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Без предмета") }, onClick = { onSelect(null); expanded = false })
            subjects.forEach { subject -> DropdownMenuItem(text = { Text(subject.name) }, onClick = { onSelect(subject.id); expanded = false }) }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DateInput(label: String, value: String, onChange: (String) -> Unit) {
    var dialog by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        IconButton(onClick = { dialog = true }) { Icon(Icons.Default.CalendarMonth, "Выбрать дату") }
    }
    if (dialog) {
        val initialMillis = remember(value) { runCatching { java.time.LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(onDismissRequest = { dialog = false }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis -> onChange(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()) }
                dialog = false
            }) { Text("Готово") }
        }, dismissButton = { TextButton(onClick = { dialog = false }) { Text("Отмена") } }) { DatePicker(state) }
    }
}

@Composable
private fun LectureEditor(initial: Lecture, subjects: List<Subject>, onDismiss: () -> Unit, onAttach: (((List<Attachment>) -> Unit) -> Unit), onOpenAttachment: (Attachment) -> Unit, onDelete: (() -> Unit)?, onSave: (Lecture) -> Unit) {
    var title by remember(initial.id) { mutableStateOf(initial.title) }
    var date by remember(initial.id) { mutableStateOf(initial.date) }
    var subject by remember(initial.id) { mutableStateOf(initial.subjectId) }
    var content by remember(initial.id) { mutableStateOf(initial.content) }
    val attachments = remember(initial.id) { mutableStateListOf<Attachment>().also { it.addAll(initial.attachments) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.title.isBlank()) "Новая лекция" else "Лекция") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                DateInput("Дата · ГГГГ-ММ-ДД", date, { date = it })
                SubjectPicker(subject, subjects, { subject = it })
                OutlinedTextField(content, { content = it }, label = { Text("Конспект / Markdown") }, modifier = Modifier.fillMaxWidth(), minLines = 5, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onAttach { attachments.addAll(it) } }) { Icon(Icons.Default.AttachFile, null); Spacer(Modifier.width(6.dp)); Text("Прикрепить") }
                    Text("  ${attachments.size} файлов", color = Muted, style = MaterialTheme.typography.labelMedium)
                }
                attachments.forEach { file ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(file.name, modifier = Modifier.weight(1f).clickable { onOpenAttachment(file) }, color = Sage, maxLines = 1)
                        IconButton(onClick = { attachments.remove(file) }) { Icon(Icons.Default.Delete, "Удалить вложение") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            if (date.isBlank()) return@TextButton
            onSave(initial.copy(title = title.trim(), date = date.trim(), subjectId = subject, content = content, attachments = attachments.toList(), updatedAt = System.currentTimeMillis()))
        }) { Text("Сохранить") } },
        dismissButton = { Row {
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onDismiss) { Text("Отмена") }
        } })
}

@Composable
private fun HomeworkEditor(initial: Homework, subjects: List<Subject>, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (Homework) -> Unit) {
    var text by remember(initial.id) { mutableStateOf(initial.text) }
    var added by remember(initial.id) { mutableStateOf(initial.addedDate.ifBlank { LocalDate.now().toString() }) }
    var due by remember(initial.id) { mutableStateOf(initial.dueDate) }
    var subject by remember(initial.id) { mutableStateOf(initial.subjectId) }
    var done by remember(initial.id) { mutableStateOf(initial.done) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.text.isBlank()) "Новое ДЗ" else "Домашнее задание") },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(text, { text = it }, label = { Text("Что нужно сделать") }, modifier = Modifier.fillMaxWidth(), minLines = 4, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            SubjectPicker(subject, subjects, { subject = it })
            DateInput("Дата добавления", added, { added = it })
            DateInput("Срок сдачи · необязательно", due, { due = it })
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(done, { done = it }); Text("Выполнено") }
        } },
        confirmButton = { TextButton(onClick = { if (added.isNotBlank()) onSave(initial.copy(text = text.trim(), addedDate = added, dueDate = due, subjectId = subject, done = done)) }) { Text("Сохранить") } },
        dismissButton = { Row { if (onDelete != null) TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = onDismiss) { Text("Отмена") } } })
}

@Composable
private fun SubjectEditor(initial: Subject?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Новый предмет" else "Переименовать предмет") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
private fun SyncDialog(sync: LanSync, store: DataStore, onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var receiving by remember { mutableStateOf(sync.isAccepting()) }
    var peers by remember { mutableStateOf<List<Peer>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Перенос между устройствами") },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Подключите оба устройства к одной Wi‑Fi сети и оставьте приложение открытым.", color = Muted)
            Surface(color = Panel2, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Принимать данные", fontWeight = FontWeight.Medium); Text("Полученная копия заменит данные на этом телефоне. Перед заменой создаётся резервная копия.", style = MaterialTheme.typography.bodySmall, color = Muted) }
                    Switch(checked = receiving, onCheckedChange = { receiving = it; sync.setAccepting(it) })
                }
            }
            OutlinedButton(onClick = { searching = true; scope.launch { peers = runCatching { sync.discover() }.getOrDefault(emptyList()); searching = false } }, enabled = !searching, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text(if (searching) "Ищем устройства…" else "Найти устройства")
            }
            if (peers.isEmpty() && !searching) Text("Устройств пока не найдено.", color = Muted, style = MaterialTheme.typography.bodySmall)
            peers.forEach { peer ->
                Surface(color = Panel2, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable(enabled = !sending) {
                    sending = true
                    scope.launch { runCatching { sync.send(peer, store.exportJson()) }.onSuccess { onMessage("Данные переданы на ${peer.name}") }.onFailure { onMessage("Не удалось передать данные: ${it.message}") }; sending = false }
                }) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(peer.name, fontWeight = FontWeight.Medium); Text(peer.ip, color = Muted, style = MaterialTheme.typography.labelSmall) }
                        Text(if (sending) "Отправка…" else "Отправить", color = Sage, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Text("Передача работает только в локальной сети Wi‑Fi.", color = Muted, style = MaterialTheme.typography.labelSmall)
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } })
}

private fun Uri.toAttachment(context: android.content.Context): Attachment? = runCatching {
    val bytes = context.contentResolver.openInputStream(this)?.use { it.readBytes() } ?: return null
    require(bytes.size <= 50 * 1024 * 1024) { "Файл больше 50 МБ" }
    val name = context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
    } ?: "attachment"
    val mime = context.contentResolver.getType(this) ?: "application/octet-stream"
    val type = if (mime.startsWith("image/")) "image" else if (mime == "application/pdf") "pdf" else mime
    val data = "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    Attachment(name = name, type = type, data = data)
}.getOrNull()

private fun openAttachment(context: android.content.Context, file: Attachment) {
    runCatching {
        val header = file.data.substringBefore(',')
        val payload = file.data.substringAfter(",", file.data)
        val bytes = android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
        val mime = Regex("data:([^;]+)").find(header)?.groupValues?.getOrNull(1)
            ?: if (file.type == "image") "image/*" else if (file.type == "pdf") "application/pdf" else file.type
        val temp = File(context.cacheDir, "attachments").apply { mkdirs() }
        val target = File(temp, file.name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        target.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", target)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }.onFailure { Toast.makeText(context, "Не удалось открыть файл: ${it.message}", Toast.LENGTH_LONG).show() }
}
