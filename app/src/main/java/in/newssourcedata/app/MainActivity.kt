package `in`.newssourcedata.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val SUPABASE_URL = "https://cmvvcwcavopmpwkowhnz.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_iY6fpaaFMuRpBpShDVSCtA_OeRglNDw"

private val supabase = createSupabaseClient(supabaseUrl = SUPABASE_URL, supabaseKey = SUPABASE_KEY) {
    install(Postgrest)
}

@Serializable
private data class CloudNewsInsert(
    val headline: String, val body: String,
    @SerialName("location_text") val locationText: String? = null
)
@Serializable
private data class CloudAssignmentInsert(
    val title: String, val description: String,
    @SerialName("due_at") val dueAt: String? = null
)
@Serializable
private data class CloudContactInsert(
    @SerialName("full_name") val fullName: String, val phone: String, val notes: String
)
@Serializable
private data class CloudNews(
    val headline: String, val body: String? = null,
    @SerialName("location_text") val locationText: String? = null
)
@Serializable
private data class CloudAssignment(
    val title: String, val description: String? = null,
    @SerialName("due_at") val dueAt: String? = null
)
@Serializable
private data class CloudContact(
    @SerialName("full_name") val fullName: String, val phone: String? = null, val notes: String? = null
)

private enum class Page { LIST, FORM }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = NewsDatabase.getInstance(applicationContext).newsDao()
        setContent {
            val colors = lightColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF5B4695),
                onPrimary = androidx.compose.ui.graphics.Color.White,
                background = androidx.compose.ui.graphics.Color(0xFFF8F7FB),
                surface = androidx.compose.ui.graphics.Color.White,
                surfaceVariant = androidx.compose.ui.graphics.Color(0xFFF0EDF6)
            )
            MaterialTheme(colorScheme = colors) { NewsSourceScreen(dao) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsSourceScreen(dao: NewsDao) {
    var tab by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf(Page.LIST) }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(listOf<String>()) }
    var message by remember { mutableStateOf("உங்கள் பதிவுகள் இந்தச் சாதனத்தில் சேமிக்கப்படும்.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun refreshLocal() {
        entries = when (tab) {
            0 -> dao.getNews().map { listOf(it.title, it.district, it.details).filter(String::isNotBlank).joinToString("\n") }
            1 -> dao.getAssignments().map { listOf(it.title, it.dueDate, it.details).filter(String::isNotBlank).joinToString("\n") }
            else -> dao.getContacts().map { listOf(it.name, it.phone, it.notes).filter(String::isNotBlank).joinToString("\n") }
        }
    }
    suspend fun refreshCloud() {
        entries = when (tab) {
            0 -> supabase.from("news_items").select().decodeList<CloudNews>()
                .map { listOf(it.headline, it.locationText, it.body).filterNotNull().filter(String::isNotBlank).joinToString("\n") }
            1 -> supabase.from("assignments").select().decodeList<CloudAssignment>()
                .map { listOf(it.title, it.dueAt, it.description).filterNotNull().filter(String::isNotBlank).joinToString("\n") }
            else -> supabase.from("contacts").select().decodeList<CloudContact>()
                .map { listOf(it.fullName, it.phone, it.notes).filterNotNull().filter(String::isNotBlank).joinToString("\n") }
        }
    }
    LaunchedEffect(tab) {
        search = ""; title = ""; details = ""; extra = ""; page = Page.LIST
        refreshLocal()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text("News Source Data", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text("செய்தியாளர் உதவியாளர்", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (page == Page.FORM) TextButton(onClick = { page = Page.LIST }) {
                        Text("←", fontSize = 20.sp)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("📰", fontSize = 18.sp) }, label = { Text("செய்திகள்", fontSize = 11.sp) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("📋", fontSize = 18.sp) }, label = { Text("அசைன்மென்ட்", fontSize = 10.sp) })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("☎", fontSize = 18.sp) }, label = { Text("தொடர்புகள்", fontSize = 11.sp) })
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (page == Page.LIST) {
                Text(when (tab) { 0 -> "செய்திகள்"; 1 -> "அசைன்மென்ட்"; else -> "தொடர்புகள்" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 23.sp, fontWeight = FontWeight.Bold))
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("தலைப்பு / விவரம் தேடுக", fontSize = 13.sp) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        title = ""; details = ""; extra = ""; message = ""
                        page = Page.FORM
                    }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 11.dp)) {
                        Text(when (tab) { 0 -> "+ புதிய செய்தி"; 1 -> "+ புதிய அசைன்மென்ட்"; else -> "+ புதிய தொடர்பு" }, fontSize = 13.sp)
                    }
                    OutlinedButton(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            try { refreshCloud(); message = "Cloud தரவு ஏற்றப்பட்டது" }
                            catch (e: Exception) { refreshLocal(); message = "Cloud ஏற்ற முடியவில்லை. சாதனத் தரவு காட்டப்படுகிறது." }
                            finally { busy = false }
                        }
                    }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp)) { Text("Cloud", fontSize = 13.sp) }
                }
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                val filtered = entries.filter { it.contains(search, ignoreCase = true) }
                if (filtered.isEmpty()) {
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(if (search.isBlank()) "பதிவுகள் இல்லை" else "தேடலுக்குப் பொருத்தமான பதிவுகள் இல்லை",
                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (search.isBlank()) "முதல் பதிவைச் சேர்க்க + புதிய பொத்தானைத் தொடவும்." else "வேறு சொல்லை உள்ளிட்டு முயற்சிக்கவும்.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtered) { entry ->
                            val lines = entry.split("\n")
                            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(lines.firstOrNull().orEmpty(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
                                    lines.drop(1).filter(String::isNotBlank).forEachIndexed { index, line ->
                                        Text(line, fontSize = if (index == 0) 11.sp else 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                                    }
                                    if (tab == 0 || tab == 1) {
                                        OutlinedButton(
                                            onClick = {
                                                val shareText = (if (tab == 0) "📰 செய்தி" else "📋 அசைன்மென்ட்") + "\n\n" + entry
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                                }
                                                context.startActivity(Intent.createChooser(sendIntent, "WhatsApp-ல் பகிரவும்"))
                                            },
                                            modifier = Modifier.align(Alignment.End),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                                        ) { Text("WhatsApp-ல் பகிர்", fontSize = 12.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Text(when (tab) { 0 -> "புதிய செய்தி"; 1 -> "புதிய அசைன்மென்ட்"; else -> "புதிய தொடர்பு" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold))
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (tab == 2) "பெயர்" else "தலைப்பு", fontSize = 13.sp) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = details, onValueChange = { details = it }, modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    label = { Text(if (tab == 2) "தொலைபேசி எண்" else "செய்தி / பணி விவரங்கள்", fontSize = 13.sp) },
                    minLines = 4, textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = extra, onValueChange = { extra = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(when (tab) { 0 -> "மாவட்டம் / இடம்"; 1 -> "கடைசி தேதி (விருப்பம்)"; else -> "குறிப்புகள் (விருப்பம்)" }, fontSize = 13.sp) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(enabled = !busy, onClick = {
                    if (title.isBlank()) { message = "தலைப்பு அல்லது பெயரை உள்ளிடுங்கள்" }
                    else {
                        val savedTitle = title.trim(); val savedDetails = details.trim(); val savedExtra = extra.trim()
                        scope.launch {
                            busy = true
                            try {
                                when (tab) {
                                    0 -> dao.insertNews(NewsItem(title = savedTitle, details = savedDetails, district = savedExtra))
                                    1 -> dao.insertAssignment(Assignment(title = savedTitle, details = savedDetails, dueDate = savedExtra))
                                    else -> dao.insertContact(Contact(name = savedTitle, phone = savedDetails, notes = savedExtra))
                                }
                                message = "சாதனத்தில் சேமிக்கப்பட்டது."
                                try {
                                    when (tab) {
                                        0 -> supabase.from("news_items").insert(CloudNewsInsert(savedTitle, savedDetails, savedExtra.ifBlank { null }))
                                        1 -> supabase.from("assignments").insert(CloudAssignmentInsert(savedTitle, savedDetails, savedExtra.ifBlank { null }))
                                        else -> supabase.from("contacts").insert(CloudContactInsert(savedTitle, savedDetails, savedExtra))
                                    }
                                    message = "சாதனத்திலும் Cloud-லும் சேமிக்கப்பட்டது."
                                } catch (cloudError: Exception) {
                                    message = "சாதனத்தில் சேமிக்கப்பட்டது; Cloud சேமிப்பு அனுமதி தேவை (RLS)."
                                }
                                title = ""; details = ""; extra = ""; page = Page.LIST
                                refreshLocal()
                            } catch (e: Exception) {
                                message = "சாதனத்தில் சேமிக்க முடியவில்லை. மீண்டும் முயற்சிக்கவும்."
                            } finally { busy = false }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp)) {
                    Text(if (busy) "சேமிக்கிறது…" else "சேமிக்கவும்", fontSize = 14.sp)
                }
                OutlinedButton(onClick = { page = Page.LIST }, modifier = Modifier.fillMaxWidth()) { Text("ரத்து செய்", fontSize = 13.sp) }
                if (message.isNotBlank()) Text(message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
