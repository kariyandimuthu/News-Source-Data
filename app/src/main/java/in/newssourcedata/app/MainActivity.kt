package `in`.newssourcedata.app

import android.os.Bundle
import android.graphics.Bitmap
import java.net.URLEncoder
import java.net.URL
import java.io.FileOutputStream
import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.googlecode.tesseract.android.TessBaseAPI
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.Icons
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import android.graphics.BitmapFactory
import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import android.net.Uri
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
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
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = SupabaseApi(applicationContext)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF283B80),
                    onPrimary = Color.White,
                    secondary = Color(0xFF087F8C),
                    onSecondary = Color.White,
                    tertiary = Color(0xFFC58A32),
                    background = Color(0xFFF6F7FB),
                    surface = Color.White,
                    surfaceVariant = Color(0xFFEEF1F7),
                    onSurface = Color(0xFF172033),
                    onSurfaceVariant = Color(0xFF65708A)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                val deviceName = api.getDeviceDisplayName()
                if (deviceName.isBlank()) {
                    api.signOut()
                    page = "login"
                } else {
                    val deviceStatus = api.registerDevice(deviceName)
                    if (deviceStatus == "owner" || deviceStatus == "approved") {
                        profile = api.getProfile()
                        if (profile?.approvalStatus == "approved") {
                            page = "main"
                        } else {
                            pendingMessage = profileApprovalMessage(profile?.approvalStatus)
                            page = "pending"
                        }
                    } else {
                        pendingMessage = deviceApprovalMessage(deviceStatus)
                        page = "pending"
                    }
                }
            } catch (error: Exception) {
                loginError = error.message ?: "Supabase-ஐ அணுக முடியவில்லை."
                api.signOut()
                page = "login"
            }
        }
    }

    when (page) {
        "checking" -> CenteredMessage("சாதன அனுமதியைச் சரிபார்க்கிறது…")
        "login" -> LoginScreen(
            loading = busy,
            message = loginError,
            initialDisplayName = api.getDeviceDisplayName(),
            onLogin = { displayName, password ->
                scope.launch {
                    busy = true
                    loginError = ""
                    try {
                        if (displayName.isBlank()) throw IllegalStateException("உங்கள் பெயரை உள்ளிடவும்.")
                        api.signIn(password)
                        val deviceStatus = api.registerDevice(displayName)
                        if (deviceStatus == "owner" || deviceStatus == "approved") {
                            profile = api.getProfile()
                            if (profile?.approvalStatus == "approved") {
                                page = "main"
                            } else {
                                pendingMessage = profileApprovalMessage(profile?.approvalStatus)
                                page = "pending"
                            }
                        } else {
                            pendingMessage = deviceApprovalMessage(deviceStatus)
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
            loading = busy,
            onCheckStatus = {
                scope.launch {
                    busy = true
                    try {
                        val deviceName = api.getDeviceDisplayName()
                        val deviceStatus = api.registerDevice(deviceName)
                        if (deviceStatus == "owner" || deviceStatus == "approved") {
                            profile = api.getProfile()
                            if (profile?.approvalStatus == "approved") {
                                page = "main"
                            } else {
                                pendingMessage = profileApprovalMessage(profile?.approvalStatus)
                            }
                        } else {
                            pendingMessage = deviceApprovalMessage(deviceStatus)
                        }
                    } catch (error: Exception) {
                        pendingMessage = error.message ?: "ஒப்புதல் நிலையைப் பெற முடியவில்லை."
                    } finally {
                        busy = false
                    }
                }
            },
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
            isOwner = profile?.role == "owner" && api.getRegisteredDeviceStatus() == "owner",
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

private fun profileApprovalMessage(status: String?): String = when (status) {
    "pending" -> "இந்தக் கணக்கு நிர்வாகி ஒப்புதலுக்காகக் காத்திருக்கிறது."
    "rejected" -> "இந்தக் கணக்கிற்கு அனுமதி வழங்கப்படவில்லை. நிர்வாகியைத் தொடர்புகொள்ளவும்."
    "revoked" -> "இந்தக் கணக்கின் அனுமதி நீக்கப்பட்டுள்ளது."
    else -> "இந்தக் கணக்கிற்கான அனுமதி விவரங்கள் கிடைக்கவில்லை. Supabase profiles அட்டவணையைச் சரிபார்க்கவும்."
}

private fun deviceApprovalMessage(status: String): String = when (status) {
    "pending" -> "இந்தச் சாதனத்தின் உள்நுழைவு கோரிக்கை முதன்மைச் சாதனத்தின் ஒப்புதலுக்காகக் காத்திருக்கிறது."
    "rejected" -> "இந்தச் சாதனத்திற்கான அனுமதி மறுக்கப்பட்டுள்ளது. முதன்மைச் சாதனத்தில் மீண்டும் ஒப்புதல் அளிக்கலாம்."
    "revoked" -> "இந்தச் சாதனத்தின் அனுமதி ரத்து செய்யப்பட்டுள்ளது. நிர்வாகியைத் தொடர்புகொள்ளவும்."
    else -> "இந்தச் சாதனத்தின் அனுமதி நிலையைச் சரிபார்க்க முடியவில்லை."
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
    initialDisplayName: String,
    onLogin: (String, String) -> Unit
) {
    var displayName by remember { mutableStateOf(initialDisplayName) }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NewsSourceWordmark()
        Spacer(Modifier.height(12.dp))
        Text("செய்திகள் • அசைன்மென்ட் • தொடர்புகள்", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("உங்கள் பெயர்") },
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
            onClick = { onLogin(displayName.trim(), password) },
            enabled = !loading && displayName.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("உள்நுழைக")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "புதிய சாதனத்தின் உள்நுழைவுக்கு முதன்மைச் சாதனத்தின் ஒப்புதல் தேவை.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PendingScreen(
    message: String,
    loading: Boolean,
    onCheckStatus: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("சாதன அனுமதி தேவை", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(message)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCheckStatus, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("ஒப்புதல் நிலையைச் சரிபார்")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSignOut, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Text("வெளியேறு")
        }
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
    val notes: String = "",
    val attachmentPaths: List<String> = emptyList(),
    val photoUri: Uri? = null,
    val followUp: Boolean = false
)

private val categoryOptions = listOf(
    "பொது" to "general",
    "குற்றம்" to "crime",
    "காவல்" to "police",
    "அரசியல்" to "politics",
    "விபத்து" to "accident",
    "நீதிமன்றம்" to "court",
    "தொல்லியல்" to "archaeology",
    "பின்தொடர்வு" to "follow_up",
    "மக்கள் பிரச்சினை" to "public_issue"
)

private val priorityOptions = listOf(
    "குறைவு" to "low",
    "சாதாரணம்" to "normal",
    "முக்கியம்" to "high",
    "பின்தொடர்வு" to "follow_up",
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
    isOwner: Boolean,
    onSignOut: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showStockVideos by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val categoryDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var activeFormTab by remember { mutableStateOf<Int?>(null) }
    var activeEditId by remember { mutableStateOf<String?>(null) }
    var activeFormInitial by remember { mutableStateOf<RecordForm?>(null) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var showAiAssistant by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDeviceApprovalDialog by remember { mutableStateOf(false) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiNews by remember { mutableStateOf<List<NewsRecord>>(emptyList()) }
    var aiAssignments by remember { mutableStateOf<List<AssignmentRecord>>(emptyList()) }
    var aiError by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var previewNews by remember { mutableStateOf<NewsRecord?>(null) }
    var previewAssignment by remember { mutableStateOf<AssignmentRecord?>(null) }
    var refreshVersion by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var districtFilter by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf("all") }
    var categoryFilter by remember { mutableStateOf("") }
    var pageNumber by remember { mutableIntStateOf(1) }
    var showDistrictFilter by remember { mutableStateOf(false) }
    var showDateFilter by remember { mutableStateOf(false) }
    var showCategoryFilter by remember { mutableStateOf(false) }
    var districts by remember { mutableStateOf<List<District>>(emptyList()) }
    var news by remember { mutableStateOf<List<NewsRecord>>(emptyList()) }
    var assignments by remember { mutableStateOf<List<AssignmentRecord>>(emptyList()) }
    var contacts by remember { mutableStateOf<List<ContactRecord>>(emptyList()) }
    var stockVideos by remember { mutableStateOf<List<StockVideoRecord>>(emptyList()) }
    var aiStockVideos by remember { mutableStateOf<List<StockVideoRecord>>(emptyList()) }
    val scope = rememberCoroutineScope()

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

    LaunchedEffect(showAiAssistant) {
        if (showAiAssistant) {
            aiLoading = true
            aiError = ""
            try {
                aiNews = api.getNews()
                aiAssignments = api.getAssignments()
                aiStockVideos = runCatching { api.getStockVideos() }.getOrDefault(emptyList())
            } catch (error: Exception) {
                aiError = error.message ?: "தரவுகளைப் பெற முடியவில்லை."
            } finally {
                aiLoading = false
            }
        }
    }

    LaunchedEffect(selectedTab, refreshVersion) {
        stockVideos = runCatching { api.getStockVideos() }.getOrDefault(emptyList())
    }

    val districtNames = remember(districts) { districts.associate { it.id to it.name } }
    val matchingStockVideos = remember(stockVideos, searchText) {
        val query = searchText.trim()
        if (query.isBlank()) emptyList() else stockVideos.filter { video ->
            video.title.contains(query, true) ||
                video.fileName.orEmpty().contains(query, true) ||
                video.driveUrl.contains(query, true)
        }.take(8)
    }

    val context = LocalContext.current

    if (showStockVideos) {
        StockVideosScreen(api = api, onBack = { showStockVideos = false; refreshVersion += 1 })
        return
    }
    if (showInfo) {
        InfoScreen(api = api, onBack = { showInfo = false; refreshVersion += 1 })
        return
    }

    // Handle Android system Back before the activity closes.
    BackHandler(
        enabled = activeFormTab != null || previewNews != null || previewAssignment != null ||
            pendingDelete != null || showDistrictFilter || showDateFilter || showCategoryFilter ||
            addMenuExpanded || showAiAssistant || showPasswordDialog || showDeviceApprovalDialog || showStockVideos || selectedTab != 0
    ) {
        when {
            showStockVideos -> showStockVideos = false
            activeFormTab != null -> {
                activeFormTab = null
                activeEditId = null
                activeFormInitial = null
            }
            previewNews != null -> previewNews = null
            previewAssignment != null -> previewAssignment = null
            pendingDelete != null -> pendingDelete = null
            showDistrictFilter -> showDistrictFilter = false
            showDateFilter -> showDateFilter = false
            showCategoryFilter -> showCategoryFilter = false
            addMenuExpanded -> addMenuExpanded = false
            showPasswordDialog -> showPasswordDialog = false
            showDeviceApprovalDialog -> showDeviceApprovalDialog = false
            showAiAssistant -> showAiAssistant = false
            selectedTab != 0 -> selectedTab = 0
        }
    }

    val categoryFilterOptions = remember(news, assignments) {
        val dynamic = (news.map { it.category } + assignments.map { it.category })
            .filter { it.isNotBlank() }.distinct().map { it to it }
        (listOf("அனைத்து வகைகள்" to "") + categoryOptions + dynamic).distinctBy { it.second }
            .map { (label, value) -> (if (value.isBlank()) "அனைத்து வகைகள்" else categoryDisplayName(value)) to value }
    }
    LaunchedEffect(selectedTab, searchText, districtFilter, dateFilter, categoryFilter) { pageNumber = 1 }
    if (showDistrictFilter) FilterOptionsDialog(
        title = "மாவட்டம்",
        choices = listOf("அனைத்து மாவட்டங்கள்" to "") + districts.map { it.name to it.id },
        selected = districtFilter, onSelect = { districtFilter = it; showDistrictFilter = false },
        onDismiss = { showDistrictFilter = false }
    )
    if (showDateFilter) FilterOptionsDialog(
        title = "கால வரம்பு", choices = dateFilterOptions, selected = dateFilter,
        onSelect = { dateFilter = it; showDateFilter = false }, onDismiss = { showDateFilter = false }
    )
    if (showCategoryFilter) FilterOptionsDialog(
        title = "செய்தி வகை", choices = categoryFilterOptions, selected = categoryFilter,
        onSelect = { categoryFilter = it; showCategoryFilter = false }, onDismiss = { showCategoryFilter = false }
    )

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

    previewAssignment?.let { item ->
        Dialog(
            onDismissRequest = { previewAssignment = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                    Text("அசைன்மென்ட் முன்னோட்டம்", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(item.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    val meta = listOf(item.eventDate, districtNames[item.districtId].orEmpty(), item.location.orEmpty())
                        .filter { it.isNotBlank() && it != "மாவட்டம் குறிப்பிடப்படவில்லை" }.joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodyLarge)
                    }
                    Button(onClick = { previewAssignment = null }, modifier = Modifier.fillMaxWidth()) { Text("மூடு") }
                }
            }
        }
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
                        if (item.attachmentPaths.isNotEmpty()) NewsPhotoStrip(api, item.attachmentPaths)
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
            draftKey = "draft_${activeFormTab ?: selectedTab}_${activeEditId ?: "new"}",
            knownCategories = (news.map { it.category } + assignments.map { it.category })
                .filter { value -> value.isNotBlank() && categoryOptions.none { it.second == value } }
                .distinct()
                .map { value -> value to value },
            onBack = {
                activeFormTab = null
                activeEditId = null
                activeFormInitial = null
            },
            onSave = { form ->
                val targetTab = activeFormTab ?: selectedTab
                val editId = activeEditId
                var uploadedPhotoPath: String? = null
                try {
                    if (targetTab == 0 && form.photoUri != null) {
                        statusMessage = "புகைப்படத்தைச் சுருக்கி பதிவேற்றுகிறது…"
                        uploadedPhotoPath = api.uploadNewsPhoto(form.photoUri)
                    }
                    val attachmentPaths = (form.attachmentPaths + listOfNotNull(uploadedPhotoPath)).distinct()
                    when (targetTab) {
                        0 -> if (editId == null) {
                            api.addNews(form.title, form.details, form.summary, form.date, form.districtId, form.category, form.priority, form.location, attachmentPaths, if (form.followUp) listOf("follow_up") else emptyList())
                        } else {
                            api.updateNews(editId, form.title, form.details, form.summary, form.date, form.districtId, form.category, form.priority, form.location, attachmentPaths, if (form.followUp) listOf("follow_up") else emptyList())
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
                } catch (error: Exception) {
                    uploadedPhotoPath?.let { path -> runCatching { api.deleteNewsPhoto(path) } }
                    throw error
                }
                statusMessage = if (uploadedPhotoPath != null) "செய்தியும் புகைப்படமும் சேமிக்கப்பட்டன." else ""
                refreshVersion += 1
                activeFormTab = null
                activeEditId = null
                activeFormInitial = null
            }
        )
        return
    }

    if (showAiAssistant) {
        AiAssistantDialog(
            api = api,
            news = aiNews,
            assignments = aiAssignments,
            stockVideos = aiStockVideos,
            loading = aiLoading,
            error = aiError,
            onDismiss = { showAiAssistant = false }
        )
    }
    if (showPasswordDialog) {
        SharedPasswordDialog(
            api = api,
            onDismiss = { showPasswordDialog = false },
            onPasswordChanged = {
                showPasswordDialog = false
                onSignOut()
            }
        )
    }

    if (showDeviceApprovalDialog) {
        DeviceApprovalDialog(
            api = api,
            onDismiss = { showDeviceApprovalDialog = false }
        )
    }

    ModalNavigationDrawer(
        drawerState = categoryDrawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("செய்தி வகைகள்", modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    NavigationDrawerItem(
                        label = { Text("அனைத்து பதிவுகள்") },
                        selected = categoryFilter.isBlank(),
                        onClick = {
                            selectedTab = 0
                            categoryFilter = ""
                            searchText = ""
                            districtFilter = ""
                            scope.launch { categoryDrawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    categoryFilterOptions.filter { it.second.isNotBlank() }.forEach { option ->
                        NavigationDrawerItem(
                            label = { Text(option.first, maxLines = 1) },
                            selected = categoryFilter == option.second,
                            onClick = {
                                selectedTab = 0
                                categoryFilter = option.second
                                searchText = ""
                                districtFilter = ""
                                scope.launch { categoryDrawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                }
            }
        }
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NewsSourceWordmark(compact = true)
                        Text(listOf("செய்திகள்", "அசைன்மென்ட்", "தொடர்புகள்")[selectedTab], style = MaterialTheme.typography.titleSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { categoryDrawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "செய்தி வகைகள்")
                    }
                },
                actions = {
                    api.getDeviceDisplayName().takeIf { it.isNotBlank() }?.let {
                        Text(it.take(18), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    TextButton(onClick = onSignOut, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("வெளியேறு", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.height(62.dp), windowInsets = WindowInsets(0.dp), tonalElevation = 2.dp) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; districtFilter = ""; searchText = ""; categoryFilter = "" },
                    icon = { Text("📰", fontSize = 16.sp) },
                    label = { Text("செய்திகள்", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; districtFilter = ""; searchText = ""; categoryFilter = "" },
                    icon = { Text("📅", fontSize = 16.sp) },
                    label = { Text("அசைன்மென்ட்", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2; districtFilter = ""; searchText = ""; categoryFilter = "" },
                    icon = { Text("👥", fontSize = 16.sp) },
                    label = { Text("தொடர்புகள்", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = showInfo,
                    onClick = { showInfo = true },
                    icon = { Text("ℹ️", fontSize = 16.sp) },
                    label = { Text("Info", style = MaterialTheme.typography.labelSmall) }
                )
            }
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { addMenuExpanded = true }, modifier = Modifier.size(50.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
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
                    DropdownMenuItem(text = { Text("🎞  Stock Videos") }, onClick = {
                        addMenuExpanded = false
                        showStockVideos = true
                    })
                    DropdownMenuItem(text = { Text("🤖  AI செய்தியாளர் உதவியாளர்") }, onClick = {
                        addMenuExpanded = false
                        showAiAssistant = true
                    })
                    if (isOwner) {
                        DropdownMenuItem(text = { Text("📱  புதிய சாதனங்களுக்கு ஒப்புதல்") }, onClick = {
                            addMenuExpanded = false
                            showDeviceApprovalDialog = true
                        })
                        DropdownMenuItem(text = { Text("🔐  கடவுச்சொல் மாற்றம் / எல்லா சாதனங்களிலிருந்தும் வெளியேறு") }, onClick = {
                            addMenuExpanded = false
                            showPasswordDialog = true
                        })
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 11.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "தேடு", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { inner ->
                            Box {
                                if (searchText.isBlank()) Text("செய்தி / Assignment / தொடர்பு / Stock Video தேடுக", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                inner()
                            }
                        }
                    )
                    if (searchText.isNotBlank()) {
                        IconButton(onClick = { searchText = "" }, modifier = Modifier.size(28.dp)) { Text("×", fontSize = 20.sp) }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = districtFilter.isNotBlank(), onClick = { showDistrictFilter = true },
                    label = { Text(districts.firstOrNull { it.id == districtFilter }?.name ?: "மாவட்டம்", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    modifier = Modifier.height(32.dp)
                )
                FilterChip(
                    selected = dateFilter != "all", onClick = { showDateFilter = true },
                    label = { Text(dateFilterOptions.firstOrNull { it.second == dateFilter }?.first ?: "தேதி", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(32.dp)
                )
                if (selectedTab != 2) {
                    FilterChip(
                        selected = categoryFilter.isNotBlank(), onClick = { showCategoryFilter = true },
                        label = { Text(categoryFilter.takeIf { it.isNotBlank() }?.let(::categoryDisplayName) ?: "வகை", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        modifier = Modifier.height(32.dp)
                    )
                    if (categoryFilter.isNotBlank()) TextButton(onClick = { categoryFilter = "" }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) { Text("×") }
                }
            }
            if (statusMessage.isNotBlank()) {
                Text(statusMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                TextButton(onClick = { refreshVersion += 1 }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Text("மீண்டும் முயற்சி") }
            }
            if (!loading && matchingStockVideos.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "ஸ்டாக் வீடியோ முடிவுகள் (" + matchingStockVideos.size + ")",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showStockVideos = true }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                        Text("எல்லாம்")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    matchingStockVideos.forEach { video ->
                        StockVideoSearchCard(api = api, video = video, compact = true)
                    }
                }
            }
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                when (selectedTab) {
                    0 -> {
                        val filtered = news.filter { item ->
                            val textMatches = searchText.isBlank() || item.headline.contains(searchText, true) ||
                                item.body.contains(searchText, true) || item.summary.contains(searchText, true) ||
                                item.category.contains(searchText, true)
                            val districtMatches = districtFilter.isBlank() || item.districtId == districtFilter
                            val categoryMatches = categoryFilter.isBlank() || when (categoryFilter) {
                                "follow_up" -> item.category == "follow_up" || item.priority == "follow_up" || item.tags.contains("follow_up")
                                else -> item.category == categoryFilter
                            }
                            textMatches && districtMatches && categoryMatches && dateMatches(item.newsDate, dateFilter)
                        }
                        if (filtered.isEmpty()) {
                            EmptyListMessage(
                                if (searchText.isNotBlank() && matchingStockVideos.isNotEmpty())
                                    "இந்தத் தேடலுக்குப் பொருந்தும் செய்தி இல்லை. மேலே உள்ள Stock Videos முடிவுகளைப் பார்க்கவும்."
                                else "செய்திகள் இல்லை. + பொத்தானை அழுத்திச் சேர்க்கவும்."
                            )
                        } else {
                            val totalPages = (filtered.size + 9) / 10
                            val safePage = pageNumber.coerceIn(1, totalPages)
                            val pageItems = filtered.drop((safePage - 1) * 10).take(10)
                            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(pageItems, key = { it.id }) { item ->
                                        val districtName = districtNames[item.districtId] ?: ""
                                        NewsCard(
                                            item = item, districtName = districtName,
                                            onPreview = { previewNews = item },
                                            onEdit = {
                                                activeEditId = item.id
                                                activeFormInitial = RecordForm(item.headline, item.body, item.summary, item.newsDate, item.districtId, item.category, item.priority, item.location.orEmpty(), attachmentPaths = item.attachmentPaths, followUp = item.tags.contains("follow_up") || item.priority == "follow_up")
                                                activeFormTab = 0
                                            },
                                            onDelete = { pendingDelete = 0 to item.id },
                                            onShare = { shareToWhatsApp(context, newsShareText(item, districtName)) }
                                        )
                                    }
                                }
                                PaginationControls(filtered.size, safePage, totalPages) { pageNumber = it }
                            }
                        }
                    }
                    1 -> {
                        val filtered = assignments.filter { item ->
                            val textMatches = searchText.isBlank() || item.title.contains(searchText, true) ||
                                item.description.contains(searchText, true) || item.category.contains(searchText, true)
                            val districtMatches = districtFilter.isBlank() || item.districtId == districtFilter
                            val categoryMatches = categoryFilter.isBlank() || when (categoryFilter) {
                                "follow_up" -> item.category == "follow_up" || item.priority == "follow_up"
                                else -> item.category == categoryFilter
                            }
                            textMatches && districtMatches && categoryMatches && dateMatches(item.eventDate, dateFilter)
                        }
                        if (filtered.isEmpty()) EmptyListMessage("அசைன்மென்ட் இல்லை. + பொத்தானை அழுத்திச் சேர்க்கவும்.")
                        else {
                            val totalPages = (filtered.size + 9) / 10
                            val safePage = pageNumber.coerceIn(1, totalPages)
                            val pageItems = filtered.drop((safePage - 1) * 10).take(10)
                            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(pageItems, key = { it.id }) { item ->
                                        val districtName = districtNames[item.districtId] ?: ""
                                        AssignmentCard(
                                            item = item, districtName = districtName,
                                            onPreview = { previewAssignment = item },
                                            onEdit = {
                                                activeEditId = item.id
                                                activeFormInitial = RecordForm(item.title, item.description, date = item.eventDate, districtId = item.districtId, category = item.category, priority = item.priority, location = item.location.orEmpty())
                                                activeFormTab = 1
                                            },
                                            onDelete = { pendingDelete = 1 to item.id },
                                            onShare = { shareToWhatsApp(context, assignmentShareText(item, districtName)) }
                                        )
                                    }
                                }
                                PaginationControls(filtered.size, safePage, totalPages) { pageNumber = it }
                            }
                        }
                    }
                    else -> {
                        val filtered = contacts.filter { item ->
                            val textMatches = searchText.isBlank() || item.fullName.contains(searchText, true) ||
                                item.phone.contains(searchText, true) || item.organization.contains(searchText, true) ||
                                item.designation.contains(searchText, true)
                            val districtMatches = districtFilter.isBlank() || item.districtId == districtFilter
                            textMatches && districtMatches && dateMatches(item.createdAt ?: "", dateFilter)
                        }
                        if (filtered.isEmpty()) EmptyListMessage("தொடர்புகள் இல்லை. + பொத்தானை அழுத்திச் சேர்க்கவும்.")
                        else {
                            val totalPages = (filtered.size + 9) / 10
                            val safePage = pageNumber.coerceIn(1, totalPages)
                            val pageItems = filtered.drop((safePage - 1) * 10).take(10)
                            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(pageItems, key = { it.id }) { item ->
                                        val districtName = districtNames[item.districtId] ?: ""
                                        ContactCard(
                                            item = item, districtName = districtName,
                                            onEdit = {
                                                activeEditId = item.id
                                                activeFormInitial = RecordForm(item.fullName, date = "", districtId = item.districtId, designation = item.designation, organization = item.organization, phone = item.phone, email = item.email, notes = item.notes)
                                                activeFormTab = 2
                                            },
                                            onDelete = { pendingDelete = 2 to item.id }
                                        )
                                    }
                                }
                                PaginationControls(filtered.size, safePage, totalPages) { pageNumber = it }
                            }
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
private fun FilterOptionsDialog(
    title: String,
    choices: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    TextButton(onClick = { onSelect(choice.second) }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (selected == choice.second) "✓  " else "") + choice.first, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("மூடு") } }
    )
}

@Composable
private fun PaginationControls(totalItems: Int, currentPage: Int, totalPages: Int, onPageChange: (Int) -> Unit) {
    if (totalItems <= 0) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text("${totalItems} பதிவுகள் · ${(currentPage - 1) * 10 + 1}–${minOf(currentPage * 10, totalItems)}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (totalPages > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (currentPage > 1) onPageChange(currentPage - 1) }, enabled = currentPage > 1, modifier = Modifier.size(30.dp)) {
                    Text("‹", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                }
                (1..totalPages).forEach { page ->
                    val selected = page == currentPage
                    Surface(
                        onClick = { onPageChange(page) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(horizontal = 2.dp).size(29.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${page}", style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                IconButton(onClick = { if (currentPage < totalPages) onPageChange(currentPage + 1) }, enabled = currentPage < totalPages, modifier = Modifier.size(30.dp)) {
                    Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
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
private fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    IconButton(onClick = onClick, modifier = Modifier.size(31.dp)) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun NewsCard(item: NewsRecord, districtName: String, onPreview: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onShare: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 9.dp, bottom = 9.dp, end = 5.dp),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(categoryDisplayName(item.category), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Surface(shape = RoundedCornerShape(6.dp), color = if (item.priority == "high" || item.priority == "urgent" || item.priority == "follow_up") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                        Text(priorityDisplayName(item.priority), modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(item.headline, style = MaterialTheme.typography.titleMedium, maxLines = 3)
                val meta = listOf(item.newsDate, districtName, item.location.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                val previewBody = articleBodyWithoutRepeatedHeadline(item.headline, item.body)
                if (previewBody.isNotBlank()) Text(previewBody, style = MaterialTheme.typography.bodySmall, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                IconAction(Icons.Default.Visibility, "முன்னோட்டம்", onPreview, MaterialTheme.colorScheme.primary)
                IconAction(Icons.Default.Edit, "திருத்து", onEdit)
                IconAction(Icons.Default.Share, "WhatsApp பகிர்வு", onShare, MaterialTheme.colorScheme.secondary)
                IconAction(Icons.Default.Delete, "நீக்கு", onDelete, MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StockVideoSearchCard(
    api: SupabaseApi,
    video: StockVideoRecord,
    compact: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember(video.id) { mutableStateOf(false) }
    ElevatedCard(
        modifier = if (compact) Modifier.width(250.dp) else Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            video.fileName?.takeIf { it.isNotBlank() && !it.equals(video.title, true) }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.previewUrl))) }
                            .onFailure { Toast.makeText(context, "Google Drive Preview-ஐத் திறக்க முடியவில்லை.", Toast.LENGTH_LONG).show() }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) { Text("▶ Preview", style = MaterialTheme.typography.labelMedium) }
                Button(
                    enabled = !downloading,
                    onClick = {
                        scope.launch {
                            downloading = true
                            try {
                                val fileName = api.downloadGoogleDriveVideo(context, video)
                                Toast.makeText(context, "பதிவிறக்கம் முடிந்தது: Downloads/NewsSourceData/" + fileName, Toast.LENGTH_LONG).show()
                            } catch (error: Exception) {
                                Toast.makeText(context, error.message ?: "வீடியோ பதிவிறக்கம் தோல்வியடைந்தது.", Toast.LENGTH_LONG).show()
                            } finally {
                                downloading = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    if (downloading) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    else Text("↓ Download", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun AssignmentCard(item: AssignmentRecord, districtName: String, onPreview: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onShare: () -> Unit) {
    val day = item.eventDate.split("-").getOrNull(2)?.takeIf { it.isNotBlank() } ?: "—"
    val month = try { LocalDate.parse(item.eventDate).month.name.take(3) } catch (_: Exception) { "" }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 9.dp, top = 9.dp, bottom = 9.dp, end = 5.dp),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.width(48.dp)) {
                Column(modifier = Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (month.isNotBlank()) Text(month, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(day, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(listOf(categoryDisplayName(item.category), priorityDisplayName(item.priority)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
                if (item.description.isNotBlank()) Text(stripSavedEventTime(item.description), style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val locationLine = listOf(districtName, item.location.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                if (locationLine.isNotBlank()) Text(locationLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                val eventTime = readSavedStartTime(item.description).let { start ->
                    val finish = readSavedEndTime(item.description)
                    listOf(start, finish).filter { it.isNotBlank() }.joinToString(" – ")
                }
                if (eventTime.isNotBlank()) Text("◷  $eventTime", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                IconAction(Icons.Default.Visibility, "முன்னோட்டம்", onPreview, MaterialTheme.colorScheme.primary)
                IconAction(Icons.Default.Edit, "திருத்து", onEdit)
                IconAction(Icons.Default.Share, "WhatsApp பகிர்வு", onShare, MaterialTheme.colorScheme.secondary)
                IconAction(Icons.Default.Delete, "நீக்கு", onDelete, MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ContactCard(item: ContactRecord, districtName: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val initials = item.fullName.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Text(initials.ifBlank { "?" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                val roleLine = listOf(item.designation, item.organization).filter { it.isNotBlank() }.joinToString(" · ")
                if (roleLine.isNotBlank()) Text(roleLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                if (item.phone.isNotBlank()) Text(item.phone, style = MaterialTheme.typography.bodySmall)
                if (item.email.isNotBlank()) Text(item.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (districtName.isNotBlank()) Text(districtName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (item.notes.isNotBlank()) Text(item.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                IconAction(Icons.Default.Call, "அழைக்க", {
                    if (item.phone.isNotBlank()) runCatching {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.phone.trim()}")))
                    }
                }, MaterialTheme.colorScheme.primary)
                IconAction(Icons.Default.Chat, "WhatsApp", {
                    val digits = item.phone.filter { it.isDigit() }
                    val international = if (digits.length == 10) "91$digits" else digits
                    if (international.isNotBlank()) runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$international")))
                    }
                }, MaterialTheme.colorScheme.secondary)
                IconAction(Icons.Default.Edit, "திருத்து", onEdit)
                IconAction(Icons.Default.Delete, "நீக்கு", onDelete, MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun shareToWhatsApp(context: Context, message: String) {
    val encoded = URLEncoder.encode(message, "UTF-8")
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=$encoded")))
}

private fun newsShareText(item: NewsRecord, district: String): String =
    listOf("📰 ${item.headline}", articleBodyWithoutRepeatedHeadline(item.headline, item.body), "📅 ${item.newsDate}",
        district.takeIf { it.isNotBlank() }?.let { "📍 $it" }, "— News Source")
        .filterNotNull().filter { it.isNotBlank() }.joinToString("\n\n")

private fun assignmentShareText(item: AssignmentRecord, district: String): String =
    listOf("📌 ${item.title}", stripSavedEventTime(item.description), "📅 ${item.eventDate}",
        readSavedStartTime(item.description).takeIf { it.isNotBlank() }?.let { start ->
            "🕒 $start" + readSavedEndTime(item.description).takeIf { it.isNotBlank() }?.let { " – $it" }.orEmpty()
        },
        district.takeIf { it.isNotBlank() }?.let { "📍 $it" },
        item.location?.takeIf { it.isNotBlank() }?.let { "இடம்: $it" }, "— News Source")
        .filterNotNull().filter { it.isNotBlank() }.joinToString("\n\n")

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordFormScreen(
    tab: Int,
    districts: List<District>,
    draftKey: String,
    initialForm: RecordForm? = null,
    knownCategories: List<Pair<String, String>> = emptyList(),
    onBack: () -> Unit,
    onSave: suspend (RecordForm) -> Unit
) {
    val context = LocalContext.current
    val draftPreferences = remember(context) { context.getSharedPreferences("news_source_form_drafts", Context.MODE_PRIVATE) }
    var draftRestored by remember(draftKey) { mutableStateOf(false) }
    var title by remember(tab, initialForm) { mutableStateOf(initialForm?.title ?: "") }
    var details by remember(tab, initialForm) { mutableStateOf(initialForm?.details?.let { if (tab == 1) stripSavedEventTime(it) else it } ?: "") }
    var summary by remember(tab, initialForm) { mutableStateOf(initialForm?.summary ?: "") }
    var date by remember(tab, initialForm) { mutableStateOf(initialForm?.date?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()) }
    var districtId by remember(tab, initialForm) { mutableStateOf(initialForm?.districtId.orEmpty()) }
    var category by remember(tab, initialForm) { mutableStateOf(initialForm?.category ?: "general") }
    var priority by remember(tab, initialForm) { mutableStateOf(initialForm?.priority ?: "normal") }
    var followUpTag by remember(tab, initialForm) { mutableStateOf(initialForm?.followUp ?: false) }
    var location by remember(tab, initialForm) { mutableStateOf(initialForm?.location ?: "") }
    var designation by remember(tab, initialForm) { mutableStateOf(initialForm?.designation ?: "") }
    var organization by remember(tab, initialForm) { mutableStateOf(initialForm?.organization ?: "") }
    var phone by remember(tab, initialForm) { mutableStateOf(initialForm?.phone ?: "") }
    var email by remember(tab, initialForm) { mutableStateOf(initialForm?.email ?: "") }
    var notes by remember(tab, initialForm) { mutableStateOf(initialForm?.notes ?: "") }
    var startTime by remember(tab, initialForm) { mutableStateOf(initialForm?.details?.let(::readSavedStartTime).orEmpty()) }
    var endTime by remember(tab, initialForm) { mutableStateOf(initialForm?.details?.let(::readSavedEndTime).orEmpty()) }
    var customCategories by remember(tab, initialForm, knownCategories) {
        mutableStateOf(
            if (initialForm?.category.isNullOrBlank() || categoryOptions.any { it.second == initialForm?.category } ||
                knownCategories.any { it.second == initialForm?.category }) emptyList()
            else listOf(initialForm?.category.orEmpty() to initialForm?.category.orEmpty())
        )
    }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDistrictPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var showPriorityPicker by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var locationDraft by remember { mutableStateOf("") }
    var summaryDraft by remember { mutableStateOf("") }
    var newCategoryName by remember { mutableStateOf("") }
    var activeTimeTarget by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var ocrRunning by remember { mutableStateOf(false) }
    var ocrMenuExpanded by remember { mutableStateOf(false) }
    var selectedPhotoUri by remember(tab, initialForm) { mutableStateOf(initialForm?.photoUri) }
    var pendingCameraOcrFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraOcrUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(draftKey) {
        val saved = draftPreferences.getString(draftKey, null)
        if (!saved.isNullOrBlank()) {
            runCatching { JSONObject(saved) }.onSuccess { draft ->
                title = draft.optString("title", title)
                details = draft.optString("details", details)
                summary = draft.optString("summary", summary)
                date = draft.optString("date", date)
                districtId = draft.optString("districtId", districtId)
                category = draft.optString("category", category)
                priority = draft.optString("priority", priority)
                followUpTag = draft.optBoolean("followUpTag", followUpTag)
                location = draft.optString("location", location)
                designation = draft.optString("designation", designation)
                organization = draft.optString("organization", organization)
                phone = draft.optString("phone", phone)
                email = draft.optString("email", email)
                notes = draft.optString("notes", notes)
                startTime = draft.optString("startTime", startTime)
                endTime = draft.optString("endTime", endTime)
                draft.optString("photoUri").takeIf { it.isNotBlank() }?.let { selectedPhotoUri = Uri.parse(it) }
                draft.optJSONArray("customCategories")?.let { values ->
                    customCategories = buildList {
                        for (index in 0 until values.length()) {
                            val value = values.optString(index).trim()
                            if (value.isNotBlank()) add(value to value)
                        }
                    }
                }
                message = "முந்தைய வரைவு மீட்டெடுக்கப்பட்டது. சேமிப்பை மீண்டும் முயற்சிக்கவும்."
            }.onFailure {
                draftPreferences.edit().remove(draftKey).apply()
            }
        }
        draftRestored = true
    }

    LaunchedEffect(
        draftKey, draftRestored, title, details, summary, date, districtId, category, priority, followUpTag,
        location, designation, organization, phone, email, notes, startTime, endTime, selectedPhotoUri, customCategories
    ) {
        if (!draftRestored) return@LaunchedEffect
        delay(600)
        val snapshot = JSONObject()
            .put("title", title).put("details", details).put("summary", summary).put("date", date)
            .put("districtId", districtId).put("category", category).put("priority", priority)
            .put("followUpTag", followUpTag).put("location", location).put("designation", designation)
            .put("organization", organization).put("phone", phone).put("email", email).put("notes", notes)
            .put("startTime", startTime).put("endTime", endTime)
            .put("photoUri", selectedPhotoUri?.toString().orEmpty())
            .put("customCategories", JSONArray(customCategories.map { it.second }))
            .put("attachmentPaths", JSONArray(initialForm?.attachmentPaths.orEmpty()))
        draftPreferences.edit().putString(draftKey, snapshot.toString()).apply()
    }

    fun processOcr(uri: Uri, temporaryFile: File? = null) {
        scope.launch {
            ocrRunning = true
            message = ""
            try {
                val recognizedText = performTamilEnglishOcr(context, uri).trim()
                if (recognizedText.isBlank()) {
                    message = "படத்தில் வாசிக்கக்கூடிய உரை கிடைக்கவில்லை."
                } else {
                    when (tab) {
                        0, 1 -> details = if (details.isBlank()) recognizedText else details.trimEnd() + "\n\n" + recognizedText
                        else -> {
                            val firstLine = recognizedText.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                            if (title.isBlank()) title = firstLine.take(150)
                            val detectedPhone = Regex("(?<!\\d)(?:\\+?\\d[\\d\\s().-]{7,}\\d)(?!\\d)").find(recognizedText)?.value?.trim()
                            if (phone.isBlank()) phone = detectedPhone.orEmpty()
                            notes = if (notes.isBlank()) recognizedText else notes.trimEnd() + "\n\n" + recognizedText
                        }
                    }
                    message = "படத்திலிருந்து உரை எடுக்கப்பட்டது. சரிபார்த்து சேமிக்கவும்."
                }
            } catch (error: Exception) {
                message = error.message ?: "OCR செய்ய முடியவில்லை. மீண்டும் முயற்சிக்கவும்."
            } finally {
                ocrRunning = false
                temporaryFile?.delete()
                if (temporaryFile != null && temporaryFile == pendingCameraOcrFile) {
                    pendingCameraOcrFile = null
                    pendingCameraOcrUri = null
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) processOcr(uri)
    }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            selectedPhotoUri = uri
            message = "படம் இணைக்கப்பட்டது. பதிவேற்றும்போது தானாகச் சுருக்கப்படும்."
        }
    }
    val cameraOcrLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val capturedUri = pendingCameraOcrUri
        val capturedFile = pendingCameraOcrFile
        if (captured && capturedUri != null && capturedFile != null) {
            processOcr(capturedUri, capturedFile)
        } else {
            capturedFile?.delete()
            pendingCameraOcrFile = null
            pendingCameraOcrUri = null
            if (!captured) message = "கேமரா படம் எடுக்கப்படவில்லை."
        }
    }
    fun launchOcrCamera() {
        try {
            val directory = File(context.cacheDir, "ocr-captures")
            if (!directory.exists() && !directory.mkdirs()) {
                throw IllegalStateException("கேமரா தற்காலிக கோப்புறையை உருவாக்க முடியவில்லை.")
            }
            val file = File(directory, "ocr-${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCameraOcrFile = file
            pendingCameraOcrUri = uri
            cameraOcrLauncher.launch(uri)
        } catch (error: Exception) {
            pendingCameraOcrFile?.delete()
            pendingCameraOcrFile = null
            pendingCameraOcrUri = null
            message = error.message ?: "கேமராவைத் திறக்க முடியவில்லை."
        }
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateToMillis(date))
    val timePickerState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
    val formTitle = when (tab) {
        0 -> if (initialForm == null) "புதிய செய்தி" else "செய்தியைத் திருத்து"
        1 -> if (initialForm == null) "புதிய அசைன்மென்ட்" else "அசைன்மென்டைத் திருத்து"
        else -> if (initialForm == null) "புதிய தொடர்பு" else "தொடர்பைத் திருத்து"
    }
    val autoTitle = details.trim().split(Regex("\\n\\s*\\n|\\r?\\n")).firstOrNull()?.trim().orEmpty().take(180)
    val selectedDistrictName = districts.firstOrNull { it.id == districtId }?.name ?: "மாவட்டம் தேர்வு"
    val allCategories = (categoryOptions + knownCategories + customCategories).distinctBy { it.second }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { date = millisToDate(it) }
                    showDatePicker = false
                }) { Text("தேர்வு") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("ரத்து") } }
        ) { DatePicker(state = datePickerState, showModeToggle = true) }
    }

    if (showDistrictPicker) {
        AlertDialog(
            onDismissRequest = { showDistrictPicker = false },
            title = { Text("மாவட்டத்தைத் தேர்வு செய்க") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { districtId = ""; showDistrictPicker = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("மாவட்டம் குறிப்பிட வேண்டாம்", modifier = Modifier.fillMaxWidth())
                    }
                    districts.forEach { district ->
                        TextButton(onClick = { districtId = district.id; showDistrictPicker = false }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (district.id == districtId) "✓  " else "") + district.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDistrictPicker = false }) { Text("மூடு") } }
        )
    }

    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("செய்தி வகை") },
            text = {
                Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                    allCategories.forEach { option ->
                        TextButton(onClick = { category = option.second; showCategoryPicker = false }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (option.second == category) "✓  " else "") + option.first, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Divider()
                    TextButton(onClick = { showCategoryPicker = false; newCategoryName = ""; showNewCategoryDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("＋ புதிய வகையைச் சேர்க்கவும்")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCategoryPicker = false }) { Text("மூடு") } }
        )
    }

    if (showNewCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showNewCategoryDialog = false },
            title = { Text("புதிய செய்தி வகை") },
            text = {
                OutlinedTextField(value = newCategoryName, onValueChange = { newCategoryName = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("எ.கா. கல்வி, சுகாதாரம்") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val newValue = newCategoryName.trim()
                    if (newValue.isNotBlank()) {
                        customCategories = (customCategories + (newValue to newValue)).distinctBy { it.second }
                        category = newValue
                        showNewCategoryDialog = false
                    }
                }) { Text("சேர்க்கவும்") }
            },
            dismissButton = { TextButton(onClick = { showNewCategoryDialog = false }) { Text("ரத்து") } }
        )
    }

    if (showPriorityPicker) {
        AlertDialog(
            onDismissRequest = { showPriorityPicker = false },
            title = { Text("முன்னுரிமை") },
            text = {
                Column {
                    priorityOptions.forEach { option ->
                        TextButton(onClick = { priority = option.second; showPriorityPicker = false }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (priority == option.second) "✓  " else "") + option.first, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPriorityPicker = false }) { Text("மூடு") } }
        )
    }

    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text(if (category == "police") "காவல் நிலையம் / இடம்" else "இடம்") },
            text = {
                OutlinedTextField(value = locationDraft, onValueChange = { locationDraft = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (category == "police") "காவல் நிலையப் பெயர்" else "இடம் (விருப்பம்)") }, singleLine = true)
            },
            confirmButton = { TextButton(onClick = { location = locationDraft.trim(); showLocationDialog = false }) { Text("சரி") } },
            dismissButton = { TextButton(onClick = { showLocationDialog = false }) { Text("ரத்து") } }
        )
    }

    if (showSummaryDialog) {
        AlertDialog(
            onDismissRequest = { showSummaryDialog = false },
            title = { Text("செய்திச் சுருக்கம்") },
            text = {
                OutlinedTextField(value = summaryDraft, onValueChange = { summaryDraft = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("சுருக்கமாக எழுதுங்கள்") }, minLines = 3)
            },
            confirmButton = { TextButton(onClick = { summary = summaryDraft.trim(); showSummaryDialog = false }) { Text("சரி") } },
            dismissButton = { TextButton(onClick = { showSummaryDialog = false }) { Text("ரத்து") } }
        )
    }

    if (activeTimeTarget != null) {
        AlertDialog(
            onDismissRequest = { activeTimeTarget = null },
            title = { Text(if (activeTimeTarget == "start") "தொடக்க நேரம்" else "முடிவு நேரம்") },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { TimePicker(state = timePickerState) } },
            confirmButton = {
                TextButton(onClick = {
                    val picked = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                    if (activeTimeTarget == "start") startTime = picked else endTime = picked
                    activeTimeTarget = null
                }) { Text("நேரத்தைத் தேர்வு செய்க") }
            },
            dismissButton = { TextButton(onClick = { activeTimeTarget = null }) { Text("ரத்து") } }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(formTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (tab == 0) "விரைவாக எழுதுங்கள்" else if (tab == 1) "நிகழ்வைத் திட்டமிடுங்கள்" else "தொடர்பைச் சேமியுங்கள்",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("✕", style = MaterialTheme.typography.titleMedium) } },
                actions = {
                    Box {
                        IconButton(
                            onClick = { ocrMenuExpanded = true },
                            enabled = !saving && !ocrRunning
                        ) {
                            if (ocrRunning) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.DocumentScanner, contentDescription = "படத்திலிருந்து உரை எடுக்க", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = ocrMenuExpanded, onDismissRequest = { ocrMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("கேலரியில் இருந்து OCR") },
                                onClick = { ocrMenuExpanded = false; imagePicker.launch(arrayOf("image/*")) }
                            )
                            DropdownMenuItem(
                                text = { Text("கேமரா மூலம் OCR") },
                                onClick = { ocrMenuExpanded = false; launchOcrCamera() }
                            )
                        }
                    }
                    if (tab == 0) {
                        IconButton(
                            onClick = { attachmentPicker.launch(arrayOf("image/*")) },
                            enabled = !saving
                        ) { Text("🖼", fontSize = 21.sp) }
                    }
                    Button(
                        onClick = {
                            when {
                                tab == 2 && title.isBlank() -> message = "பெயரை எழுதுங்கள்."
                                tab != 2 && details.isBlank() -> message = "செய்தியின் விவரத்தை எழுதுங்கள்."
                                tab == 2 && phone.isBlank() && email.isBlank() -> message = "தொலைபேசி எண் அல்லது மின்னஞ்சலை எழுதுங்கள்."
                                else -> scope.launch {
                                    saving = true
                                    message = if (tab == 0) "செய்தியைச் சரிபார்த்து தமிழில் தயாரிக்கிறது…" else ""
                                    try {
                                        val sourceDetails = details.trim()
                                        val translatedDetails = if (tab == 0) translateEnglishToTamilIfNeeded(sourceDetails) else sourceDetails
                                        val translatedSummary = if (tab == 0) translateEnglishToTamilIfNeeded(summary.trim()) else summary.trim()
                                        val resolvedTitle = if (tab == 2) title.trim() else translatedDetails
                                            .split(Regex("\\n\\s*\\n|\\r?\\n")).firstOrNull()?.trim().orEmpty().take(180)
                                        if (resolvedTitle.isBlank()) throw IllegalStateException("செய்தியின் தலைப்பை உருவாக்க முடியவில்லை.")
                                        details = translatedDetails
                                        summary = translatedSummary
                                        val savedDetails = if (tab == 1 && (startTime.isNotBlank() || endTime.isNotBlank())) {
                                            stripSavedEventTime(translatedDetails).trim() + "\n\nநிகழ்வு நேரம்: ${startTime.ifBlank { "குறிப்பிடப்படவில்லை" }} - ${endTime.ifBlank { "குறிப்பிடப்படவில்லை" }}"
                                        } else translatedDetails
                                        onSave(RecordForm(
                                            title = resolvedTitle, details = savedDetails, summary = translatedSummary, date = date,
                                            districtId = districtId.takeIf { it.isNotBlank() }, category = category, priority = priority,
                                            location = location.trim(), designation = designation.trim(), organization = organization.trim(),
                                            phone = phone.trim(), email = email.trim(), notes = notes.trim(),
                                            attachmentPaths = initialForm?.attachmentPaths.orEmpty(), photoUri = selectedPhotoUri,
                                            followUp = followUpTag
                                        ))
                                        draftPreferences.edit().remove(draftKey).apply()
                                    } catch (error: Exception) {
                                        message = error.message ?: "சேமிக்க முடியவில்லை."
                                    } finally {
                                        saving = false
                                    }
                                }
                            }
                        },
                        enabled = !saving,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("சேமி  ✓", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (tab == 2) {
                BasicTextField(
                    value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { inner ->
                        Box {
                            if (title.isBlank()) Text("தொடர்பின் பெயர்", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    }
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                EditorLine(phone, { phone = it }, "தொலைபேசி எண்")
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                EditorLine(designation, { designation = it }, "பதவி")
                EditorLine(organization, { organization = it }, "அலுவலகம் / நிறுவனம்")
                EditorLine(email, { email = it }, "மின்னஞ்சல்")
                EditorLine(notes, { notes = it }, "குறிப்புகள்")
                AssistChip(onClick = { showDistrictPicker = true }, label = { Text("⌖  $selectedDistrictName") }, shape = RoundedCornerShape(14.dp))
            } else {
                if (tab == 0 && selectedPhotoUri != null) {
                    val chosenPhoto = selectedPhotoUri
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AndroidView(
                            factory = { viewContext -> ImageView(viewContext).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                setImageURI(chosenPhoto)
                            } },
                            update = { it.setImageURI(chosenPhoto) },
                            modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("செய்திப் புகைப்படம்", style = MaterialTheme.typography.labelLarge)
                            Text("சேமிக்கும்போது சிறிய JPG அளவுக்கு சுருக்கப்படும்.", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { selectedPhotoUri = null }) { Text("நீக்கு") }
                    }
                }
                if (autoTitle.isNotBlank()) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
                            Text("தலைப்பு · தானாக", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(autoTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        }
                    }
                }
                BasicTextField(
                    value = details, onValueChange = { details = it },
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = 28.sp),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize()) {
                            if (details.isBlank()) Text(
                                if (tab == 0) "இங்கே செய்தியை எழுதத் தொடங்குங்கள்…\n\nமுதல் பத்தி தானாகத் தலைப்பாகும். தனியாக தலைப்பு உள்ளிட வேண்டியதில்லை."
                                else "நிகழ்வின் விவரத்தை எழுதத் தொடங்குங்கள்…\n\nமுதல் வரி தானாக அசைன்மென்ட் தலைப்பாகும்.",
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    }
                )
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = { datePickerState.selectedDateMillis = dateToMillis(date); showDatePicker = true }, label = { Text("▦  ${dateLabel(date)}") }, shape = RoundedCornerShape(14.dp))
                    AssistChip(onClick = { showDistrictPicker = true }, label = { Text("⌖  $selectedDistrictName") }, shape = RoundedCornerShape(14.dp))
                }
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (tab == 0) {
                        FilterChip(
                            selected = followUpTag,
                            onClick = { followUpTag = !followUpTag },
                            label = { Text(if (followUpTag) "✓ பின்தொடர்வு" else "＋ பின்தொடர்வு") },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                    AssistChip(onClick = { showCategoryPicker = true }, label = { Text("◈  ${categoryDisplayName(category)}") }, shape = RoundedCornerShape(14.dp))
                    AssistChip(onClick = { showPriorityPicker = true }, label = { Text("⚑  ${priorityDisplayName(priority)}") }, shape = RoundedCornerShape(14.dp))
                    AssistChip(onClick = { locationDraft = location; showLocationDialog = true },
                        label = { Text("⌖  ${location.ifBlank { if (category == "police") "காவல் நிலையம்" else "இடம்" }}") },
                        shape = RoundedCornerShape(14.dp))
                    if (tab == 0) AssistChip(onClick = { summaryDraft = summary; showSummaryDialog = true },
                        label = { Text("≡  ${if (summary.isBlank()) "சுருக்கம்" else "சுருக்கம் சேர்க்கப்பட்டது"}") },
                        shape = RoundedCornerShape(14.dp))
                }
                if (tab == 1) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(onClick = {
                            activeTimeTarget = "start"
                            val picked = parseTime(startTime) ?: (9 to 0)
                            timePickerState.hour = picked.first
                            timePickerState.minute = picked.second
                        }, label = { Text("◷  ${startTime.ifBlank { "தொடக்க நேரம்" }}") }, shape = RoundedCornerShape(14.dp))
                        AssistChip(onClick = {
                            activeTimeTarget = "end"
                            val picked = parseTime(endTime) ?: (10 to 0)
                            timePickerState.hour = picked.first
                            timePickerState.minute = picked.second
                        }, label = { Text("◷  ${endTime.ifBlank { "முடிவு நேரம்" }}") }, shape = RoundedCornerShape(14.dp))
                    }
                }
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EditorLine(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    BasicTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        decorationBox = { inner ->
            Box {
                if (value.isBlank()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            }
        }
    )
}

@Composable
private fun NewsSourceWordmark(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 13.dp)) {
        Surface(
            modifier = Modifier.size(if (compact) 38.dp else 76.dp),
            shape = RoundedCornerShape(if (compact) 12.dp else 23.dp),
            color = Color(0xFF1C2B63),
            shadowElevation = if (compact) 2.dp else 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("N", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, fontSize = if (compact) 22.sp else 43.sp), color = Color.White)
                Text("•", modifier = Modifier.offset(x = if (compact) 9.dp else 18.dp, y = if (compact) 8.dp else 17.dp), style = MaterialTheme.typography.titleLarge, color = Color(0xFF4DD6C8))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text("NEWS", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, letterSpacing = if (compact) 1.2.sp else 4.sp, fontSize = if (compact) 13.sp else 26.sp, lineHeight = if (compact) 16.sp else 29.sp), color = MaterialTheme.colorScheme.onSurface)
            Text("SOURCE", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = if (compact) 1.sp else 3.5.sp, fontSize = if (compact) 11.sp else 22.sp, lineHeight = if (compact) 13.sp else 25.sp), color = MaterialTheme.colorScheme.primary)
            if (!compact) Text("REPORTER WORKSPACE", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.7.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

private fun categoryDisplayName(value: String): String = categoryOptions.firstOrNull { it.second == value }?.first ?: value.ifBlank { "வகை தேர்வு" }
private fun priorityDisplayName(value: String): String = priorityOptions.firstOrNull { it.second == value }?.first ?: value.ifBlank { "சாதாரணம்" }
private fun stripSavedEventTime(value: String): String = value.replace(Regex("\\n\\s*நிகழ்வு நேரம்:\\s*.*$", RegexOption.DOT_MATCHES_ALL), "").trim()
private fun readSavedStartTime(value: String): String = Regex("நிகழ்வு நேரம்:\\s*(\\d{2}:\\d{2})\\s*-").find(value)?.groupValues?.getOrNull(1).orEmpty()
private fun readSavedEndTime(value: String): String = Regex("நிகழ்வு நேரம்:.*?-\\s*(\\d{2}:\\d{2})").find(value)?.groupValues?.getOrNull(1).orEmpty()
private fun parseTime(value: String): Pair<Int, Int>? = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(value.trim())?.let {
    (it.groupValues[1].toIntOrNull() ?: 9) to (it.groupValues[2].toIntOrNull() ?: 0)
}?.takeIf { it.first in 0..23 && it.second in 0..59 }
private fun dateToMillis(value: String): Long = runCatching {
    LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}.getOrElse { LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
private fun millisToDate(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate().toString()


private fun dateLabel(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
}.getOrDefault(value)


private suspend fun performTamilEnglishOcr(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val root = File(context.filesDir, "news_source_ocr")
    val tessdata = File(root, "tessdata")
    if (!tessdata.exists() && !tessdata.mkdirs()) {
        throw IllegalStateException("OCR தரவு கோப்புறையை உருவாக்க முடியவில்லை.")
    }
    listOf("tam", "eng").forEach { language ->
        val trainedData = File(tessdata, "$language.traineddata")
        if (!trainedData.exists() || trainedData.length() < 100_000L) {
            val partial = File(tessdata, "$language.traineddata.part")
            try {
                val connection = URL("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/$language.traineddata").openConnection()
                connection.connectTimeout = 20_000
                connection.readTimeout = 120_000
                connection.getInputStream().use { input ->
                    FileOutputStream(partial).use { output -> input.copyTo(output) }
                }
                if (!partial.exists() || partial.length() < 100_000L) {
                    throw IllegalStateException("$language OCR மொழித் தரவு முழுமையாகப் பதிவிறங்கவில்லை.")
                }
                if (trainedData.exists()) trainedData.delete()
                if (!partial.renameTo(trainedData)) throw IllegalStateException("OCR மொழித் தரவைச் சேமிக்க முடியவில்லை.")
            } finally {
                if (partial.exists()) partial.delete()
            }
        }
    }

    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("படத்தைத் திறக்க முடியவில்லை.")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sampleSize = 1
    while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 2000) sampleSize *= 2
    val bitmap = BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize }
    ) ?: throw IllegalStateException("இந்தப் படத்தை வாசிக்க முடியவில்லை.")

    val recognizer = TessBaseAPI()
    try {
        if (!recognizer.init(root.absolutePath, "tam+eng")) {
            throw IllegalStateException("தமிழ்/ஆங்கில OCR இயந்திரத்தைத் தொடங்க முடியவில்லை.")
        }
        recognizer.setImage(bitmap)
        recognizer.getUTF8Text().orEmpty().trim()
    } finally {
        recognizer.end()
        bitmap.recycle()
    }
}



@Composable
private fun NewsPhotoStrip(api: SupabaseApi, paths: List<String>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        paths.forEach { path ->
            var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(path) {
                bitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = api.getNewsPhotoBytes(path)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.getOrNull()
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(82.dp)) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "செய்திப் புகைப்படம்",
                        modifier = Modifier.size(width = 82.dp, height = 64.dp).clip(RoundedCornerShape(9.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(modifier = Modifier.size(width = 82.dp, height = 64.dp), shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Box(contentAlignment = Alignment.Center) { Text("🖼", fontSize = 21.sp) }
                    }
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val filename = api.downloadNewsPhoto(context, path)
                                Toast.makeText(context, "Downloads/NewsSourceData/$filename", Toast.LENGTH_LONG).show()
                            } catch (error: Exception) {
                                Toast.makeText(context, error.message ?: "படத்தைப் பதிவிறக்க முடியவில்லை.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                ) { Text("↓ பதிவிறக்கு", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun RemoteVideoThumbnail(url: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection().apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                connection.getInputStream().use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "YouTube preview", modifier = modifier.clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
    } else {
        Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(contentAlignment = Alignment.Center) { Text("▶", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockVideosScreen(api: SupabaseApi, onBack: () -> Unit) {
    BackHandler(enabled = true, onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videos by remember { mutableStateOf<List<StockVideoRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var showNewVideo by remember { mutableStateOf(false) }
    var videoUrl by remember { mutableStateOf("") }
    var downloadingVideoId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }

    fun pasteClipboardLink() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copied = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty().trim()
        videoUrl = copied
        if (copied.isBlank()) message = "முதலில் Google Drive வீடியோ இணைப்பைக் காப்பி செய்யுங்கள்."
        else if (extractGoogleDriveFileId(copied) == null) message = "காப்பி செய்யப்பட்ட இணைப்பு Google Drive வீடியோவாக இல்லை."
        else message = ""
    }
    fun loadVideos() {
        scope.launch {
            loading = true
            message = ""
            try { videos = api.getStockVideos() }
            catch (error: Exception) { message = error.message ?: "வீடியோக்களைப் பெற முடியவில்லை." }
            finally { loading = false }
        }
    }
    fun openPreview(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(context, "Google Drive preview-ஐத் திறக்க முடியவில்லை.", Toast.LENGTH_LONG).show() }
    }
    fun downloadVideo(video: StockVideoRecord) {
        if (downloadingVideoId != null) return
        scope.launch {
            downloadingVideoId = video.id
            message = ""
            try {
                val fileName = api.downloadGoogleDriveVideo(context, video)
                Toast.makeText(context, "பதிவிறக்கம் முடிந்தது: Downloads/NewsSourceData/$fileName", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                message = error.message ?: "வீடியோவைப் பதிவிறக்க முடியவில்லை."
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } finally {
                downloadingVideoId = null
            }
        }
    }
    LaunchedEffect(Unit) { loadVideos() }

    if (showNewVideo) {
        AlertDialog(
            onDismissRequest = { if (!saving) showNewVideo = false },
            title = { Text("புதிய வீடியோ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = videoUrl,
                        onValueChange = { videoUrl = it; message = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Google Drive இணைப்பு") },
                        placeholder = { Text("காப்பி செய்த Drive இணைப்பை இங்கே ஒட்டுங்கள்") },
                        singleLine = true
                    )
                    extractGoogleDriveFileId(videoUrl)?.let { id ->
                        RemoteVideoThumbnail("https://drive.google.com/thumbnail?id=$id&sz=w640", Modifier.fillMaxWidth().height(150.dp))
                    }
                    Text("கோப்பின் பெயர் தானாகப் பெறப்படும். Download நேரடியாக Downloads/NewsSourceData கோப்புறையில் சேமிக்கப்படும்.", style = MaterialTheme.typography.bodySmall)
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    enabled = !saving && extractGoogleDriveFileId(videoUrl) != null,
                    onClick = {
                        scope.launch {
                            saving = true
                            message = ""
                            try {
                                api.saveStockVideo(videoUrl)
                                videoUrl = ""
                                showNewVideo = false
                                loadVideos()
                            } catch (error: Exception) {
                                message = error.message ?: "வீடியோவைச் சேமிக்க முடியவில்லை."
                            } finally { saving = false }
                        }
                    }
                ) { if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("சேமி") }
            },
            dismissButton = { TextButton(onClick = { showNewVideo = false }, enabled = !saving) { Text("ரத்து") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Stock Videos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Google Drive வீடியோ இணைப்புகளும் நேரடி Download-உம்", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("← திரும்ப") } },
                actions = {
                    IconButton(onClick = { pasteClipboardLink(); showNewVideo = true }) { Text("＋🎬", fontSize = 19.sp) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { pasteClipboardLink(); showNewVideo = true },
                shape = RoundedCornerShape(16.dp)
            ) { Text("＋🎬", fontSize = 19.sp) }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Google Drive-ல் உள்ள வீடியோ இணைப்பைக் காப்பி செய்து +🎬 பொத்தானை அழுத்துங்கள். இணைப்பு தானாக நிரப்பப்படும்; தலைப்பை தனியாக எழுத வேண்டாம்.", style = MaterialTheme.typography.bodySmall)
            Text("பதிவிறக்கத்திற்கு கோப்பைப் பகிர்வதில் Anyone with the link (Viewer) அனுமதி மற்றும் Download அனுமதி இருக்க வேண்டும்.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (message.isNotBlank() && !showNewVideo) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (loading && videos.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (videos.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { EmptyListMessage("இன்னும் Stock Video இணைப்புகள் இல்லை. +🎬 பொத்தானை அழுத்துங்கள்.") }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(videos, key = { it.id }) { video ->
                        ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                RemoteVideoThumbnail(video.thumbnailUrl, Modifier.fillMaxWidth().height(170.dp))
                                Text(video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(video.driveUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(onClick = { openPreview(video.previewUrl) }, modifier = Modifier.weight(1f)) { Text("▶ Preview") }
                                    Button(
                                        enabled = downloadingVideoId == null,
                                        onClick = { downloadVideo(video) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (downloadingVideoId == video.id) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                                        else Text("↓ Download")
                                    }
                                    TextButton(onClick = {
                                        scope.launch {
                                            try {
                                                api.deleteStockVideo(video.id)
                                                videos = api.getStockVideos()
                                            } catch (error: Exception) {
                                                message = error.message ?: "வீடியோ இணைப்பை நீக்க முடியவில்லை."
                                            }
                                        }
                                    }) { Text("நீக்கு") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun translateEnglishToTamilIfNeeded(text: String): String {
    if (text.isBlank()) return text
    val englishLetters = Regex("[A-Za-z]").findAll(text).count()
    val tamilLetters = Regex("[\\u0B80-\\u0BFF]").findAll(text).count()
    if (englishLetters < 24 || englishLetters <= tamilLetters) return text

    val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.TAMIL)
            .build()
    )
    try {
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitMlKit()
        val translated = translator.translate(text).awaitMlKit().trim()
        if (translated.isBlank()) throw IllegalStateException("தமிழ் மொழிபெயர்ப்பு வெறுமையாக உள்ளது.")
        return translated
    } catch (error: Exception) {
        throw IllegalStateException("ஆங்கில செய்தியை தமிழில் மொழிபெயர்க்க முடியவில்லை. இணைய இணைப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும். ${error.message.orEmpty()}")
    } finally {
        translator.close()
    }
}

private suspend fun <T> Task<T>.awaitMlKit(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
}

@Composable
private fun AiAssistantDialog(
    api: SupabaseApi,
    news: List<NewsRecord>,
    assignments: List<AssignmentRecord>,
    stockVideos: List<StockVideoRecord>,
    loading: Boolean,
    error: String,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val followUpAssignments = assignments.filter {
        !it.status.equals("completed", true) &&
            !it.status.equals("closed", true) &&
            (it.priority == "follow_up" || it.category == "follow_up" ||
                it.title.contains("பின்தொடர", true) || it.description.contains("பின்தொடர", true))
    }
    val matchingNews = if (normalizedQuery.isBlank()) emptyList() else news.filter { item ->
        item.headline.contains(normalizedQuery, true) ||
            item.body.contains(normalizedQuery, true) ||
            item.summary.contains(normalizedQuery, true) ||
            item.category.contains(normalizedQuery, true) ||
            item.location.orEmpty().contains(normalizedQuery, true)
    }.take(8)
    val matchingAssignments = if (normalizedQuery.isBlank()) emptyList() else assignments.filter { item ->
        item.title.contains(normalizedQuery, true) ||
            item.description.contains(normalizedQuery, true) ||
            item.category.contains(normalizedQuery, true) ||
            item.location.orEmpty().contains(normalizedQuery, true)
    }.take(8)
    val matchingStockVideos = if (normalizedQuery.isBlank()) emptyList() else stockVideos.filter { video ->
        video.title.contains(normalizedQuery, true) ||
            video.fileName.orEmpty().contains(normalizedQuery, true) ||
            video.driveUrl.contains(normalizedQuery, true)
    }.take(8)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary) {
                        Text("AI", modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("செய்தியாளர் உதவியாளர்", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("உங்கள் Supabase தரவுகளில் தேடுகிறது", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text("மூடு") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("செய்தி / இடம் / Assignment / Stock Video தேடுக") },
                    placeholder = { Text("எ.கா. நாங்குநேரி, திருட்டு…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true
                )
                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("பின்தொடர வேண்டிய பணிகள்", fontWeight = FontWeight.Bold)
                                if (followUpAssignments.isEmpty()) {
                                    Text("தற்போது தனியாகக் குறிக்கப்பட்ட Follow-up Assignment இல்லை.", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    followUpAssignments.take(5).forEach { item ->
                                        Text("• ${item.title} — ${item.eventDate.ifBlank { "தேதி குறிப்பிடவில்லை" }}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    if (normalizedQuery.isNotBlank()) {
                        item { Text("கிடைத்த செய்திகள் (${matchingNews.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        if (matchingNews.isEmpty()) {
                            item { Text("இந்தச் சொல்லுக்குப் பொருந்தும் செய்தி இல்லை.", style = MaterialTheme.typography.bodySmall) }
                        } else {
                            items(matchingNews, key = { "news-${it.id}" }) { item ->
                                Card {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(item.headline, fontWeight = FontWeight.SemiBold)
                                        Text("${item.newsDate} · ${item.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (item.summary.isNotBlank()) Text(item.summary, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                                    }
                                }
                            }
                        }
                        item { Text("கிடைத்த Assignments (${matchingAssignments.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        if (matchingAssignments.isEmpty()) {
                            item { Text("பொருந்தும் Assignment இல்லை.", style = MaterialTheme.typography.bodySmall) }
                        } else {
                            items(matchingAssignments, key = { "assignment-${it.id}" }) { item ->
                                Card {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(item.title, fontWeight = FontWeight.SemiBold)
                                        Text("${item.eventDate} · ${item.status}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                                    }
                                }
                            }
                        }
                        item {
                            Text(
                                "கிடைத்த Stock Videos (" + matchingStockVideos.size + ")",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (matchingStockVideos.isEmpty()) {
                            item { Text("பொருந்தும் Stock Video இல்லை.", style = MaterialTheme.typography.bodySmall) }
                        } else {
                            items(matchingStockVideos, key = { "stock-video-" + it.id }) { video ->
                                StockVideoSearchCard(api = api, video = video, compact = false)
                            }
                        }
                    } else {
                        item {
                            Text(
                                "இது முதல் கட்ட இலவச உதவியாளர். இப்போது சேமித்த செய்திகளைத் தேடவும் Follow-up பட்டியலைப் பார்க்கவும் முடியும். இயல்பான மொழியில் பதிலளிக்கும் வெளிப்புற AI இன்னும் இணைக்கப்படவில்லை.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text("தகவல்கள் உங்கள் செயலியில் சேமிக்கப்பட்ட பதிவுகளிலிருந்து மட்டுமே காட்டப்படுகின்றன.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DeviceApprovalDialog(
    api: SupabaseApi,
    onDismiss: () -> Unit
) {
    var devices by remember { mutableStateOf<List<AppDeviceApproval>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            errorMessage = ""
            try {
                devices = api.getPendingDeviceApprovals()
            } catch (error: Exception) {
                errorMessage = error.message ?: "நிலுவைச் சாதனங்களைப் பெற முடியவில்லை."
            } finally {
                loading = false
            }
        }
    }

    fun decide(deviceId: String, approved: Boolean) {
        scope.launch {
            loading = true
            errorMessage = ""
            try {
                api.setDeviceApproval(deviceId, approved)
                devices = api.getPendingDeviceApprovals()
            } catch (error: Exception) {
                errorMessage = error.message ?: "சாதன அனுமதியை மாற்ற முடியவில்லை."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("புதிய சாதனங்களின் ஒப்புதல்") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("நிலுவை கோரிக்கைகள்", modifier = Modifier.weight(1f))
                    TextButton(onClick = { reload() }, enabled = !loading) { Text("புதுப்பி") }
                }
                if (loading && devices.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else if (devices.isEmpty()) {
                    Text("ஒப்புதலுக்காகக் காத்திருக்கும் புதிய சாதனங்கள் இல்லை.")
                }
                devices.forEach { device ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(device.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (device.status == "rejected") "முன்பு மறுக்கப்பட்டது — மீண்டும் அனுமதிக்கலாம்"
                                else "புதிய சாதன அனுமதி கோரிக்கை",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (device.createdAt.isNotBlank()) {
                                Text("கோரிக்கை: \${device.createdAt.take(10)}", style = MaterialTheme.typography.labelSmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { decide(device.deviceId, true) },
                                    enabled = !loading,
                                    modifier = Modifier.weight(1f)
                                ) { Text("அனுமதி") }
                                OutlinedButton(
                                    onClick = { decide(device.deviceId, false) },
                                    enabled = !loading,
                                    modifier = Modifier.weight(1f)
                                ) { Text("மறுக்க") }
                            }
                        }
                    }
                }
                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("முடிந்தது") } }
    )
}

@Composable
private fun SharedPasswordDialog(
    api: SupabaseApi,
    onDismiss: () -> Unit,
    onPasswordChanged: () -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy && !completed) onDismiss() },
        title = { Text("பகிரப்பட்ட கடவுச்சொல் மாற்றம்") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (completed) {
                    Text("கடவுச்சொல் மாற்றப்பட்டது. அனைத்து refresh session-களும் ரத்து செய்யப்பட்டன; பழைய access token-களின் தரவு அணுகலும் தடுக்கப்பட்டது.")
                    Text("இந்தச் சாதனத்திலும் மீண்டும் உள்நுழைய வேண்டும்.")
                } else {
                    Text("புதிய கடவுச்சொல் அமைத்ததும் எல்லா நிருபர்களின் பழைய session-களும் நிறுத்தப்படும்.")
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it; errorMessage = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("தற்போதைய கடவுச்சொல்") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; errorMessage = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("புதிய கடவுச்சொல் (குறைந்தது 8 எழுத்துகள்)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMessage = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("புதிய கடவுச்சொல்லை உறுதிப்படுத்தவும்") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    if (errorMessage.isNotBlank()) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (completed) {
                Button(onClick = onPasswordChanged) { Text("உள்நுழைவு பக்கத்திற்குச் செல்ல") }
            } else {
                Button(
                    enabled = !busy && currentPassword.isNotBlank() && newPassword.length >= 8 &&
                        newPassword == confirmPassword && newPassword != currentPassword,
                    onClick = {
                        scope.launch {
                            busy = true
                            errorMessage = ""
                            try {
                                api.changeSharedPassword(currentPassword, newPassword)
                                completed = true
                            } catch (exception: Exception) {
                                errorMessage = exception.message ?: "கடவுச்சொல்லை மாற்ற முடியவில்லை."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("கடவுச்சொல்லை மாற்றி அனைவரையும் வெளியேற்று")
                }
            }
        },
        dismissButton = {
            if (!completed) {
                TextButton(onClick = onDismiss, enabled = !busy) { Text("ரத்து") }
            }
        }
    )
}
