package `in`.newssourcedata.app

import android.os.Bundle
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

    if (activeFormTab != null) {
        RecordFormScreen(
            tab = activeFormTab ?: selectedTab,
            districts = districts,
            onBack = { activeFormTab = null },
            onSave = { form ->
                when (activeFormTab ?: selectedTab) {
                    0 -> api.addNews(
                        headline = form.title,
                        body = form.details,
                        summary = form.summary,
                        newsDate = form.date,
                        districtId = form.districtId,
                        category = form.category,
                        priority = form.priority,
                        location = form.location
                    )
                    1 -> api.addAssignment(
                        title = form.title,
                        description = form.details,
                        eventDate = form.date,
                        districtId = form.districtId,
                        category = form.category,
                        priority = form.priority,
                        location = form.location
                    )
                    else -> api.addContact(
                        fullName = form.title,
                        designation = form.designation,
                        organization = form.organization,
                        phone = form.phone,
                        email = form.email,
                        districtId = form.districtId,
                        notes = form.notes
                    )
                }
                refreshVersion += 1
                activeFormTab = null
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
            FloatingActionButton(onClick = { activeFormTab = selectedTab }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
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
                                NewsCard(item, districtName)
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
                                AssignmentCard(item, districtName)
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
                                ContactCard(item, districtName)
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

@Composable
private fun NewsCard(item: NewsRecord, districtName: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("✎", modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.category.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(item.newsDate + " · " + districtName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            if (item.summary.isNotBlank()) Text(item.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.body.isNotBlank()) Text(item.body, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
            if (!item.location.isNullOrBlank()) Text("⌖  " + item.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(item.status, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}@Composable
private fun AssignmentCard(item: AssignmentRecord, districtName: String) {
    val dateParts = item.eventDate.split("-")
    val day = dateParts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "—"
    val month = try { LocalDate.parse(item.eventDate).month.name.take(3) } catch (_: Exception) { "DATE" }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.width(62.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(month, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(day, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.status.replace("_", " "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("· " + item.priority, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("⌖  $districtName" + if (!item.location.isNullOrBlank()) " · " + item.location else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}@Composable
private fun ContactCard(item: ContactRecord, districtName: String) {
    val initials = item.fullName.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
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
                Text(districtName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (item.notes.isNotBlank()) Text(item.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
    }
}@Composable
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
    onBack: () -> Unit,
    onSave: suspend (RecordForm) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var districtId by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("general") }
    var priority by remember { mutableStateOf("normal") }
    var location by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val formTitle = when (tab) {
        0 -> "புதிய செய்தி"
        1 -> "புதிய அசைன்மென்ட்"
        else -> "புதிய தொடர்பு"
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onBack) { Text("← திரும்ப") }
        Text(formTitle, style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (tab == 2) "பெயர் *" else "தலைப்பு *") },
            singleLine = true
        )

        if (tab != 2) {
            Text("மாவட்டம்", style = MaterialTheme.typography.labelMedium)
            DistrictDropdown(
                districts = districts,
                selectedId = districtId,
                allowAll = false,
                onSelected = { districtId = it },
                modifier = Modifier.fillMaxWidth()
            )
            Text("வகை", style = MaterialTheme.typography.labelMedium)
            ChoiceDropdown(
                selected = category,
                choices = categoryOptions,
                placeholder = "வகை தேர்வு",
                onSelected = { category = it },
                modifier = Modifier.fillMaxWidth()
            )
            Text("முன்னுரிமை", style = MaterialTheme.typography.labelMedium)
            ChoiceDropdown(
                selected = priority,
                choices = priorityOptions,
                placeholder = "முன்னுரிமை",
                onSelected = { priority = it },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (tab == 0) "செய்தி தேதி (YYYY-MM-DD)" else "நிகழ்வு தேதி (YYYY-MM-DD)") },
                singleLine = true
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("இடம் (விருப்பம்)") },
                singleLine = true
            )
            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (tab == 0) "செய்தி விவரம்" else "அசைன்மென்ட் விவரம்") },
                minLines = 3
            )
            if (tab == 0) {
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("சுருக்கம் (விருப்பம்)") },
                    minLines = 2
                )
            }
        } else {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("தொலைபேசி எண்") },
                singleLine = true
            )
            OutlinedTextField(
                value = designation,
                onValueChange = { designation = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("பதவி") },
                singleLine = true
            )
            OutlinedTextField(
                value = organization,
                onValueChange = { organization = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("நிறுவனம் / அலுவலகம்") },
                singleLine = true
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("மின்னஞ்சல்") },
                singleLine = true
            )
            Text("மாவட்டம்", style = MaterialTheme.typography.labelMedium)
            DistrictDropdown(
                districts = districts,
                selectedId = districtId,
                allowAll = false,
                onSelected = { districtId = it },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("குறிப்புகள்") },
                minLines = 2
            )
        }

        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                if (title.isBlank()) {
                    message = "பெயர் அல்லது தலைப்பை உள்ளிடவும்."
                } else if (tab != 2 && runCatching { LocalDate.parse(date) }.isFailure) {
                    message = "தேதியை YYYY-MM-DD வடிவில் உள்ளிடவும்."
                } else {
                    scope.launch {
                        saving = true
                        message = ""
                        try {
                            onSave(
                                RecordForm(
                                    title = title.trim(),
                                    details = details.trim(),
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
            modifier = Modifier.fillMaxWidth()
        ) {
            if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Supabase-ல் சேமிக்கவும்")
        }
    }
}
