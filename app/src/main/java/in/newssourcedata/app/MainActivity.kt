package `in`.newssourcedata.app

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = SupabaseApi(applicationContext)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(api)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(api: SupabaseApi) {
    var page by remember { mutableStateOf(if (api.hasSession()) "checking" else "login") }
    var profile by remember { mutableStateOf<AppProfile?>(null) }
    var loginError by remember { mutableStateOf("") }
    var pendingMessage by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (api.hasSession()) {
            try {
                profile = api.getProfile()
                if (profile?.approvalStatus == "approved") {
                    page = "main"
                } else {
                    pendingMessage = approvalMessage(profile?.approvalStatus)
                    page = "pending"
                }
            } catch (error: Exception) {
                loginError = error.message ?: "Supabase-ஐ அணுக முடியவில்லை."
                api.signOut()
                page = "login"
            }
        }
    }

    when (page) {
        "checking" -> CenteredMessage("Supabase-ஐ இணைக்கிறது…")
        "login" -> LoginScreen(
            loading = busy,
            message = loginError,
            onLogin = { email, password ->
                scope.launch {
                    busy = true
                    loginError = ""
                    try {
                        api.signIn(email, password)
                        profile = api.getProfile()
                        if (profile?.approvalStatus == "approved") {
                            page = "main"
                        } else {
                            pendingMessage = approvalMessage(profile?.approvalStatus)
                            page = "pending"
                        }
                    } catch (error: Exception) {
                        api.signOut()
                        loginError = error.message ?: "உள்நுழைய முடியவில்லை."
                    } finally {
                        busy = false
                    }
                }
            }
        )
        "pending" -> PendingScreen(
            message = pendingMessage,
            onSignOut = {
                scope.launch {
                    api.signOut()
                    profile = null
                    loginError = ""
                    page = "login"
                }
            }
        )
        else -> MainAppScreen(
            onSignOut = {
                scope.launch {
                    api.signOut()
                    profile = null
                    loginError = ""
                    page = "login"
                }
            },
            api = api
        )
    }
}

private fun approvalMessage(status: String?): String = when (status) {
    "pending" -> "இந்தக் கணக்கு நிர்வாகி ஒப்புதலுக்காகக் காத்திருக்கிறது."
    "rejected" -> "இந்தக் கணக்கிற்கு அனுமதி வழங்கப்படவில்லை. நிர்வாகியைத் தொடர்புகொள்ளவும்."
    "revoked" -> "இந்தக் கணக்கின் அனுமதி நீக்கப்பட்டுள்ளது."
    else -> "இந்தக் கணக்கிற்கான அனுமதி விவரங்கள் கிடைக்கவில்லை. Supabase profiles அட்டவணையைச் சரிபார்க்கவும்."
}

@Composable
private fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(message)
        }
    }
}

@Composable
private fun LoginScreen(
    loading: Boolean,
    message: String,
    onLogin: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("News Source Data", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("செய்திகள் • அசைன்மென்ட் • தொடர்புகள்", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("மின்னஞ்சல்") },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("கடவுச்சொல்") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        if (message.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onLogin(email.trim(), password) },
            enabled = !loading && email.contains("@") && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("உள்நுழைக")
        }
        Spacer(Modifier.height(10.dp))
        Text("இந்தப் பதிப்பில் புதிய பயனர் பதிவு இல்லை; அங்கீகரிக்கப்பட்ட கணக்கைப் பயன்படுத்தவும்.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PendingScreen(message: String, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("கணக்கு அனுமதி தேவை", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(message)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onSignOut) { Text("வெளியேறு") }
    }
}

private data class RecordForm(
    val title: String,
    val details: String = "",
    val summary: String = "",
    val date: String = "",
    val districtId: String? = null,
    val category: String = "general",
    val priority: String = "normal",
    val location: String = "",
    val designation: String = "",
    val organization: String = "",
    val phone: String = "",
    val email: String = "",
    val notes: String = ""
)

private val categoryOptions = listOf(
    "பொது" to "general",
    "குற்றம்" to "crime",
    "காவல்" to "police",
    "அரசியல்" to "politics",
    "விபத்து" to "accident",
    "நீதிமன்றம்" to "court",
    "தொல்லியல்" to "archaeology",
    "மக்கள் பிரச்சினை" to "public_issue"
)

private val priorityOptions = listOf(
    "குறைவு" to "low",
    "சாதாரணம்" to "normal",
    "முக்கியம்" to "high",
    "அவசரம்" to "urgent"
)

private val dateFilterOptions = listOf(
    "அனைத்து தேதிகள்" to "all",
    "இன்று" to "today",
    "இந்த வாரம்" to "week",
    "இந்த மாதம்" to "month",
    "இந்த ஆண்டு" to "year"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppScreen(
    api: SupabaseApi,
    onSignOut: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeFormTab by remember { mutableStateOf<Int?>(null) }
    var activeEditId by remember { mutableStateOf<String?>(null) }
    var activeFormInitial by remember { mutableStateOf<RecordForm?>(null) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var previewNews by remember { mutableStateOf<NewsRecord?>(null) }
    var refreshVersion by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var districtFilter by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf("all") }
    var districts by remember { mutableStateOf<List<District>>(emptyList()) }
    var news by remember { mutableStateOf<List<NewsRecord>>(emptyList()) }
    var assignments by remember { mutableStateOf<List<AssignmentRecord>>(emptyList()) }
    var contacts by remember { mutableStateOf<List<ContactRecord>>(emptyList()) }

    LaunchedEffect(selectedTab, refreshVersion) {
        loading = true
        statusMessage = ""
        try {
            if (districts.isEmpty()) districts = api.getDistricts()
            when (selectedTab) {
                0 -> news = api.getNews()
                1 -> assignments = api.getAssignments()
                else -> contacts = api.getContacts()
            }
        } catch (error: Exception) {
            statusMessage = error.message ?: "தரவுகளைப் பெற முடியவில்லை."
        } finally {
            loading = false
        }
    }

    val districtNames = remember(districts) { districts.associate { it.id to it.name } }

    pendingDelete?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("நீக்க வேண்டுமா?") },
            text = { Text("இந்தப் பதிவு நிரந்தரமாக நீக்கப்படும். தொடரவா?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            when (request.first) {
                                0 -> api.deleteNews(request.second)
                                1 -> api.deleteAssignment(request.second)
                                else -> api.deleteContact(request.second)
                            }
                            refreshVersion += 1
                            statusMessage = "பதிவு நீக்கப்பட்டது."
                        } catch (error: Exception) {
                            statusMessage = error.message ?: "நீக்க முடியவில்லை."
                        } finally {
                            pendingDelete = null
                        }
                    }
                }) { Text("நீக்கு") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("ரத்து") }
            }
        )
    }

    previewNews?.let { item ->
        Dialog(
            onDismissRequest = { previewNews = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                    Text("செய்தி முன்னோட்டம்", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(item.headline, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(listOf(item.newsDate, districtNames[item.districtId].orEmpty(), item.location.orEmpty())
                        .filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    val articleBody = articleBodyWithoutRepeatedHeadline(item.headline, item.body)
                    Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        if (articleBody.isNotBlank()) Text(articleBody, style = MaterialTheme.typography.bodyLarge)
                    }
                    Button(onClick = { previewNews = null }, modifier = Modifier.fillMaxWidth()) { Text("மூடு") }
                }
            }
        }
    }

    if (activeFormTab != null) {
        RecordFormScreen(
            tab = activeFormTab ?: selectedTab,
            districts = districts,
            initialForm = activeFormInitial,
            onBack = {
                activeFormTab = null
                activeEditId = null
                activeFormInitial = null
            },
            onSave = { form ->
                val targetTab = activeFormTab ?: selectedTab
                val editId = activeEditId
                when (targetTab) {
                    0 -> if (editId == null) {
                        api.addNews(form.title, form.details, form.summary, form.date, form.districtId, form.category, form.priority, form.location)
                    } else {
                        api.updateNews(editId, form.title, form.details, form.summary, form.date, form.districtId, form.category, form.priority, form.location)
                    }
                    1 -> if (editId == null) {
                        api.addAssignment(form.title, form.details, form.date, form.districtId, form.category, form.priority, form.location)
                    } else {
                        api.updateAssignment(editId, form.title, form.details, form.date, form.districtId, form.category, form.priority, form.location)
                    }
                    else -> if (editId == null) {
                        api.addContact(form.title, form.designation, form.organization, form.phone, form.email, form.districtId, form.notes)
                    } else {
                        api.updateContact(editId, form.title, form.designation, form.organization, form.phone, form.email, form.districtId, form.notes)
                    }
                }
                refreshVersion += 1
                activeFormTab = null
                activeEditId = null
                activeFormInitial = null
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listOf("செய்திகள்", "அசைன்மென்ட்", "தொடர்புகள்")[selectedTab]) },
                actions = {
                    TextButton(onClick = onSignOut) { Text("வெளியேறு") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; districtFilter = ""; searchText = "" },
                    icon = { Text("📰") },
                    label = { Text("செய்திகள்") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; districtFilter = ""; searchText = "" },
                    icon = { Text("📋") },
                    label = { Text("அசைன்மென்ட்") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2; districtFilter = ""; searchText = "" },
                    icon = { Text("☎") },
                    label = { Text("தொடர்புகள்") }
                )
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                DropdownMenu(expanded = addMenuExpanded, onDismissRequest = { addMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("📰  செய்தி சேர்க்க") }, onClick = {
                        addMenuExpanded = false; activeEditId = null; activeFormInitial = null; activeFormTab = 0
                    })
                    DropdownMenuItem(text = { Text("📋  அசைன்மென்ட் சேர்க்க") }, onClick = {
                        addMenuExpanded = false; activeEditId = null; activeFormInitial = null; activeFormTab = 1
                    })
                    DropdownMenuItem(text = { Text("☎  தொடர்பு சேர்க்க") }, onClick = {
                        addMenuExpanded = false; activeEditId = null; activeFormInitial = null; activeFormTab = 2
                    })
                }
                FloatingActionButton(onClick = { addMenuExpanded = true }) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("பெயர் / செய்தி / விவரங்களைத் தேடுக") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("மாவட்டம்", style = MaterialTheme.typography.labelMedium)
                    DistrictDropdown(
                        districts = districts,
                        selectedId = districtFilter,
                        allowAll = true,
                        onSelected = { districtFilter = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("கால வரம்பு", style = MaterialTheme.typography.labelMedium)
                    ChoiceDropdown(
                        selected = dateFilter,
                        choices = dateFilterOptions,
                        placeholder = "தேதி",
                        onSelected = { dateFilter = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (statusMessage.isNotBlank()) {
                Text(statusMessage, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { refreshVersion += 1 }) { Text("மீண்டும் முயற்சி") }
            }
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    0 -> {
                        val filtered = news.filter { item ->
                            val textMatches = searchText.isBlank() ||
                                item.headline.contains(searchText, true) ||
                                item.body.contains(searchText, true) ||
                                item.summary.contains(searchText, true) ||
                                item.category.contains(searchText, true)
                            val districtMatches = districtFilter.isBlank() || item.districtId == districtFilter
                            textMatches && districtMatches && dateMatches(item.newsDate, dateFilter)
                        }
                        if (filtered.isEmpty()) EmptyListMessage("செய்திகள் இல்லை. + பொத்தானை அழுத்திச் சேர்க்கவும்.")
                        else LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filtered, key = { it.id }) { item ->
                                val districtName = districtNames[item.districtId] ?: "மாவட்டம் குறிப்பிடப்படவில்லை"
                                NewsCard(
                                    item = item,
                                    districtName = districtName,
                                    onPreview = { previewNews = item },
                                    onEdit = {
                                        activeEditId = item.id
                                        activeFormInitial = RecordForm(item.headline, item.body, item.summary, item.newsDate, item.districtId, item.category, item.priority, item.location.orEmpty())
                                        activeFormTab = 0
                                    },
                                    onDelete = { pendingDelete = 0 to item.id }
                                )
                            }
                        }
                    }
                    1 -> {
                        val filtered = assignments.filter { item ->
                            val textMatches = searchText.isBlank() ||
                                item.title.contains(searchText, true) ||
                                item.description.contains(searchText, true) ||
                                item.category.contains(searchText, true)
                            val districtMatches = districtFilter.isBlank() || item.districtId == districtFilter
                            textMatches && districtMatches && dateMatches(item.eventDate, dateFilter)
                        }
                        if (filtered.isEmpty()) EmptyListMessage("அசைன்மென்ட் இல்லை. + பொத்தானை அழுத்திச் சேர்க்கவும்.")
                        else LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filtered, key = { it.id }) { item ->
                                val districtName = districtNames[item.districtId] ?: "மாவட்டம் குறிப்பிடப்படவில்லை"
                                AssignmentCard(
                                    item = item,
                                    districtName = districtName,
                                    onEdit = {
                                        activeEditId = item.id
                                        activeFormInitial = RecordForm(item.title, item.description, date = item.eventDate, districtId = item.districtId, category = item.category, priority = item.priority, location = item.location.orEmpty())
                                        activeFormTab = 1
                                    },
                                    onDelete = { pendingDelete = 1 to item.id }
                                )
                            }
                        }
                    }
                    else -> {
                        val filtered = contacts.filter { item ->
                            val textMatches = searchText.isBlank() ||
                                item.fullName.contains(searchText, true) ||
                                item.phone.contains(searchText, true) ||
                                item.organization.contains(searchText, true) ||
                                item.designation.contains(searchText, true)
                            val districtMatches = districtFilter.isBlank() || item.districtId == districtFilter
                            textMatches && districtMatches && dateMatches(item.createdAt ?: "", dateFilter)
                        }
                        if (filtered.isEmpty()) EmptyListMessage("தொடர்புகள் இல்லை. + பொத்தானை அழுத்திச் சேர்க்கவும்.")
                        else LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filtered, key = { it.id }) { item ->
                                val districtName = districtNames[item.districtId] ?: "மாவட்டம் குறிப்பிடப்படவில்லை"
                                ContactCard(
                                    item = item,
                                    districtName = districtName,
                                    onEdit = {
                                        activeEditId = item.id
                                        activeFormInitial = RecordForm(item.fullName, date = "", districtId = item.districtId, designation = item.designation, organization = item.organization, phone = item.phone, email = item.email, notes = item.notes)
                                        activeFormTab = 2
                                    },
                                    onDelete = { pendingDelete = 2 to item.id }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun dateMatches(value: String, filter: String): Boolean {
    if (filter == "all" || value.isBlank()) return true
    val recordDate = try { LocalDate.parse(value.take(10)) } catch (_: Exception) { return true }
    val today = LocalDate.now()
    return when (filter) {
        "today" -> recordDate == today
        "week" -> {
            val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            !recordDate.isBefore(start) && !recordDate.isAfter(start.plusDays(6))
        }
        "month" -> recordDate.year == today.year && recordDate.month == today.month
        "year" -> recordDate.year == today.year
        else -> true
    }
}

@Composable
private fun EmptyListMessage(message: String) {
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun articleBodyWithoutRepeatedHeadline(headline: String, body: String): String {
    val cleanBody = body.trim()
    if (cleanBody.isBlank() || headline.isBlank()) return cleanBody
    val normalizedHeadline = headline.trim().replace("\r\n", "\n")
    val normalizedBody = cleanBody.replace("\r\n", "\n")
    val firstParagraph = normalizedBody.substringBefore("\n\n").trim()
    if (firstParagraph.equals(normalizedHeadline, ignoreCase = true)) {
        return normalizedBody.substringAfter("\n\n", "").trim()
    }
    val firstLine = normalizedBody.lineSequence().firstOrNull()?.trim().orEmpty()
    return if (firstLine.equals(normalizedHeadline, ignoreCase = true)) {
        normalizedBody.lineSequence().drop(1).joinToString("\n").trim()
    } else cleanBody
}

@Composable
private fun NewsCard(
    item: NewsRecord,
    districtName: String,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.category.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(listOf(item.newsDate, districtName).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = MaterialTheme.shapes.small, color = when (item.priority) {
                    "urgent" -> MaterialTheme.colorScheme.errorContainer
                    "high" -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }) {
                    Text(item.priority, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(item.headline, style = MaterialTheme.typography.titleLarge)
            val previewBody = articleBodyWithoutRepeatedHeadline(item.headline, item.body)
            if (previewBody.isNotBlank()) Text(previewBody, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
            if (!item.location.isNullOrBlank()) Text("⌖  " + item.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f)) { Text("முன்னோட்டம்") }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("திருத்து") }
                TextButton(onClick = onDelete) { Text("நீக்கு", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun AssignmentCard(
    item: AssignmentRecord,
    districtName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateParts = item.eventDate.split("-")
    val day = dateParts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "—"
    val month = try { LocalDate.parse(item.eventDate).month.name.take(3) } catch (_: Exception) { "" }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.width(62.dp)) {
                    Column(modifier = Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (month.isNotBlank()) Text(month, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(day, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(listOf(item.status.replace("_", " "), item.priority).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val locationLine = listOf(districtName, item.location.orEmpty()).filter { it.isNotBlank() && it != "மாவட்டம் குறிப்பிடப்படவில்லை" }.joinToString(" · ")
                    if (locationLine.isNotBlank()) Text("⌖  $locationLine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("திருத்து") }
                TextButton(onClick = onDelete) { Text("நீக்கு", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun ContactCard(
    item: ContactRecord,
    districtName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val initials = item.fullName.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                        Text(initials.ifBlank { "?" }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.fullName, style = MaterialTheme.typography.titleMedium)
                    val roleLine = listOf(item.designation, item.organization).filter { it.isNotBlank() }.joinToString(" · ")
                    if (roleLine.isNotBlank()) Text(roleLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.phone.isNotBlank()) Text("☎  " + item.phone, style = MaterialTheme.typography.bodyMedium)
                    if (item.email.isNotBlank()) Text("✉  " + item.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (districtName.isNotBlank() && districtName != "மாவட்டம் குறிப்பிடப்படவில்லை") Text(districtName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (item.notes.isNotBlank()) Text(item.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.phone.trim()}"))
                        context.startActivity(dialIntent)
                    },
                    enabled = item.phone.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("அழைக்க") }
                OutlinedButton(
                    onClick = {
                        val digits = item.phone.filter { it.isDigit() }
                        val international = if (digits.length == 10) "91$digits" else digits
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$international")))
                    },
                    enabled = item.phone.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("WhatsApp") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("திருத்து") }
                TextButton(onClick = onDelete) { Text("நீக்கு", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun DistrictDropdown(
    districts: List<District>,
    selectedId: String,
    allowAll: Boolean,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = districts.firstOrNull { it.id == selectedId }?.name
        ?: if (allowAll) "அனைத்து மாவட்டங்கள்" else "மாவட்டம் தேர்வு"

    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedName, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowAll) {
                DropdownMenuItem(
                    text = { Text("அனைத்து மாவட்டங்கள்") },
                    onClick = { onSelected(""); expanded = false }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("மாவட்டம் தேர்வு செய்யவில்லை") },
                    onClick = { onSelected(""); expanded = false }
                )
            }
            districts.forEach { district ->
                DropdownMenuItem(
                    text = { Text(district.name) },
                    onClick = { onSelected(district.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ChoiceDropdown(
    selected: String,
    choices: List<Pair<String, String>>,
    placeholder: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.second == selected }?.first ?: placeholder
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedLabel, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.first) },
                    onClick = { onSelected(choice.second); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun RecordFormScreen(
    tab: Int,
    districts: List<District>,
    initialForm: RecordForm? = null,
    onBack: () -> Unit,
    onSave: suspend (RecordForm) -> Unit
) {
    var title by remember(tab, initialForm) { mutableStateOf(initialForm?.title ?: "") }
    var details by remember(tab, initialForm) { mutableStateOf(initialForm?.details ?: "") }
    var summary by remember(tab, initialForm) { mutableStateOf(initialForm?.summary ?: "") }
    var date by remember(tab, initialForm) { mutableStateOf(initialForm?.date?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()) }
    var districtId by remember(tab, initialForm) { mutableStateOf(initialForm?.districtId.orEmpty()) }
    var category by remember(tab, initialForm) { mutableStateOf(initialForm?.category ?: "general") }
    var priority by remember(tab, initialForm) { mutableStateOf(initialForm?.priority ?: "normal") }
    var location by remember(tab, initialForm) { mutableStateOf(initialForm?.location ?: "") }
    var designation by remember(tab, initialForm) { mutableStateOf(initialForm?.designation ?: "") }
    var organization by remember(tab, initialForm) { mutableStateOf(initialForm?.organization ?: "") }
    var phone by remember(tab, initialForm) { mutableStateOf(initialForm?.phone ?: "") }
    var email by remember(tab, initialForm) { mutableStateOf(initialForm?.email ?: "") }
    var notes by remember(tab, initialForm) { mutableStateOf(initialForm?.notes ?: "") }
    var startTime by remember(tab, initialForm) { mutableStateOf("") }
    var endTime by remember(tab, initialForm) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val formTitle = when (tab) {
        0 -> if (initialForm == null) "புதிய செய்தி" else "செய்தியைத் திருத்து"
        1 -> if (initialForm == null) "புதிய அசைன்மென்ட்" else "அசைன்மென்டைத் திருத்து"
        else -> if (initialForm == null) "புதிய தொடர்பு" else "தொடர்பைத் திருத்து"
    }
    val autoTitle = details.trim().split(Regex("\\n\\s*\\n|\\r?\\n")).firstOrNull()?.trim().orEmpty().take(180)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(formTitle) },
                navigationIcon = { TextButton(onClick = onBack) { Text("← திரும்ப") } }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 6.dp) {
                Button(
                    onClick = {
                        val resolvedTitle = if (tab == 2) title.trim() else autoTitle
                        when {
                            resolvedTitle.isBlank() -> message = if (tab == 2) "பெயரை உள்ளிடவும்." else "செய்தியின் முதல் பத்தியை உள்ளிடவும்; அதுவே தலைப்பாகும்."
                            tab != 2 && runCatching { LocalDate.parse(date) }.isFailure -> message = "தேதியை YYYY-MM-DD வடிவில் உள்ளிடவும்."
                            tab == 2 && phone.isBlank() && email.isBlank() -> message = "தொலைபேசி எண் அல்லது மின்னஞ்சலை உள்ளிடவும்."
                            else -> scope.launch {
                                saving = true
                                message = ""
                                try {
                                    val savedDetails = if (tab == 1 && (startTime.isNotBlank() || endTime.isNotBlank())) {
                                        "நிகழ்வு நேரம்: ${startTime.ifBlank { "குறிப்பிடப்படவில்லை" }} - ${endTime.ifBlank { "குறிப்பிடப்படவில்லை" }}\n\n" + details.trim()
                                    } else details.trim()
                                    onSave(
                                        RecordForm(
                                            title = resolvedTitle,
                                            details = savedDetails,
                                            summary = summary.trim(),
                                            date = date.trim(),
                                            districtId = districtId.takeIf { it.isNotBlank() },
                                            category = category,
                                            priority = priority,
                                            location = location.trim(),
                                            designation = designation.trim(),
                                            organization = organization.trim(),
                                            phone = phone.trim(),
                                            email = email.trim(),
                                            notes = notes.trim()
                                        )
                                    )
                                } catch (error: Exception) {
                                    message = error.message ?: "சேமிக்க முடியவில்லை."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(if (initialForm == null) "சேமிக்கவும்" else "மாற்றத்தைச் சேமிக்கவும்")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (tab == 2) {
                OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("பெயர் *") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("தொலைபேசி எண்") }, singleLine = true)
                OutlinedTextField(value = designation, onValueChange = { designation = it }, modifier = Modifier.fillMaxWidth(), label = { Text("பதவி") }, singleLine = true)
                OutlinedTextField(value = organization, onValueChange = { organization = it }, modifier = Modifier.fillMaxWidth(), label = { Text("நிறுவனம் / அலுவலகம்") }, singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("மின்னஞ்சல்") }, singleLine = true)
                Text("மாவட்டம்", style = MaterialTheme.typography.labelMedium)
                DistrictDropdown(districts, districtId, false, { districtId = it }, Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("குறிப்புகள் (விருப்பம்)") }, minLines = 2)
            } else {
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (tab == 0) "செய்தி உள்ளடக்கம் * — முதல் பத்தி தலைப்பாகும்" else "நிகழ்வு விவரம் * — முதல் பத்தி தலைப்பாகும்") },
                    minLines = 7
                )
                if (autoTitle.isNotBlank()) {
                    Text("தானாக உருவாகும் தலைப்பு", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(autoTitle, style = MaterialTheme.typography.titleMedium)
                }
                if (tab == 0) {
                    OutlinedTextField(value = summary, onValueChange = { summary = it }, modifier = Modifier.fillMaxWidth(), label = { Text("சுருக்கம் (விருப்பம்)") }, minLines = 2)
                }
                OutlinedTextField(value = date, onValueChange = { date = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (tab == 0) "செய்தி தேதி (YYYY-MM-DD)" else "நிகழ்வு தேதி (YYYY-MM-DD)") }, singleLine = true)
                if (tab == 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = startTime, onValueChange = { startTime = it }, modifier = Modifier.weight(1f), label = { Text("தொடக்க நேரம்") }, placeholder = { Text("09:30") }, singleLine = true)
                        OutlinedTextField(value = endTime, onValueChange = { endTime = it }, modifier = Modifier.weight(1f), label = { Text("முடிவு நேரம்") }, placeholder = { Text("11:00") }, singleLine = true)
                    }
                }
                Text("மாவட்டம்", style = MaterialTheme.typography.labelMedium)
                DistrictDropdown(districts, districtId, false, { districtId = it }, Modifier.fillMaxWidth())
                Text("துறை / வகை", style = MaterialTheme.typography.labelMedium)
                ChoiceDropdown(category, categoryOptions, "வகை தேர்வு", { category = it }, Modifier.fillMaxWidth())
                Text("முன்னுரிமை", style = MaterialTheme.typography.labelMedium)
                ChoiceDropdown(priority, priorityOptions, "முன்னுரிமை", { priority = it }, Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (category == "police") "காவல் நிலையம் / இடம் (விருப்பம்)" else "இடம் (விருப்பம்)") },
                    singleLine = true
                )
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(6.dp))
        }
    }
}

