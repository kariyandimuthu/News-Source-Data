package `in`.newssourcedata.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val SUPABASE_URL = "https://cmvvcwcavopmpwkowhnz.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_iY6fpaaFMuRpBpShDVSCtA_OeRglNDw"

private val supabase = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_KEY
) {
    install(Postgrest)
}

@Serializable
private data class CloudNewsInsert(
    val headline: String,
    val body: String,
    @SerialName("location_text") val locationText: String? = null
)

@Serializable
private data class CloudAssignmentInsert(
    val title: String,
    val description: String,
    @SerialName("due_at") val dueAt: String? = null
)

@Serializable
private data class CloudContactInsert(
    @SerialName("full_name") val fullName: String,
    val phone: String,
    val notes: String
)

@Serializable
private data class CloudNews(
    val headline: String,
    val body: String? = null,
    @SerialName("location_text") val locationText: String? = null
)

@Serializable
private data class CloudAssignment(
    val title: String,
    val description: String? = null,
    @SerialName("due_at") val dueAt: String? = null
)

@Serializable
private data class CloudContact(
    @SerialName("full_name") val fullName: String,
    val phone: String? = null,
    val notes: String? = null
)

private enum class Page { LIST, FORM }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = NewsDatabase.getInstance(applicationContext).newsDao()
        setContent { MaterialTheme { NewsSourceScreen(dao) } }
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

    suspend fun refreshLocal() {
        val all = when (tab) {
            0 -> dao.getNews().map { "${it.title} — ${it.district}\n${it.details}" }
            1 -> dao.getAssignments().map { "${it.title}\n${it.details} ${it.dueDate}" }
            else -> dao.getContacts().map { "${it.name} — ${it.phone}\n${it.notes}" }
        }
        entries = all
    }

    suspend fun refreshCloud() {
        entries = when (tab) {
            0 -> supabase.from("news_items").select().decodeList<CloudNews>()
                .map { "${it.headline} — ${it.locationText.orEmpty()}\n${it.body.orEmpty()}" }
            1 -> supabase.from("assignments").select().decodeList<CloudAssignment>()
                .map { "${it.title}\n${it.description.orEmpty()} ${it.dueAt.orEmpty()}" }
            else -> supabase.from("contacts").select().decodeList<CloudContact>()
                .map { "${it.fullName} — ${it.phone.orEmpty()}\n${it.notes.orEmpty()}" }
        }
    }

    LaunchedEffect(tab) {
        search = ""
        title = ""
        details = ""
        extra = ""
        page = Page.LIST
        refreshLocal()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("News Source Data") },
                navigationIcon = {
                    if (page == Page.FORM) {
                        TextButton(onClick = { page = Page.LIST }) { Text("← திரும்ப") }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("📰") }, label = { Text("செய்திகள்") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("📋") }, label = { Text("அசைன்மென்ட்") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("☎") }, label = { Text("தொடர்புகள்") })
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (page == Page.LIST) {
                Text(
                    when (tab) { 0 -> "செய்திகள்"; 1 -> "அசைன்மென்ட்"; else -> "தொடர்புகள்" },
                    style = MaterialTheme.typography.headlineSmall
                )
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("தேடுக") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        title = ""; details = ""; extra = ""; message = ""
                        page = Page.FORM
                    }, modifier = Modifier.weight(1f)) {
                        Text(when (tab) { 0 -> "+ புதிய செய்தி"; 1 -> "+ புதிய அசைன்மென்ட்"; else -> "+ புதிய தொடர்பு" })
                    }
                    OutlinedButton(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            try {
                                refreshCloud()
                                message = "Cloud தரவு ஏற்றப்பட்டது"
                            } catch (e: Exception) {
                                refreshLocal()
                                message = "Cloud ஏற்ற முடியவில்லை; சாதனத் தரவு காட்டப்படுகிறது: ${e.message.orEmpty().take(100)}"
                            } finally { busy = false }
                        }
                    }) { Text("Cloud") }
                }
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
                if (entries.isEmpty()) {
                    Text(
                        when (tab) { 0 -> "இன்னும் செய்திகள் இல்லை. புதிய செய்தியைச் சேர்க்கவும்."; 1 -> "இன்னும் அசைன்மென்ட்கள் இல்லை. புதியதைச் சேர்க்கவும்."; else -> "இன்னும் தொடர்புகள் இல்லை. புதிய தொடர்பைச் சேர்க்கவும்." },
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(entries.filter { it.contains(search, ignoreCase = true) }) { entry ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Text(entry, modifier = Modifier.padding(14.dp))
                            }
                        }
                    }
                }
            } else {
                Text(
                    when (tab) { 0 -> "புதிய செய்தி"; 1 -> "புதிய அசைன்மென்ட்"; else -> "புதிய தொடர்பு" },
                    style = MaterialTheme.typography.headlineSmall
                )
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (tab == 2) "பெயர்" else "தலைப்பு") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = details, onValueChange = { details = it },
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    label = { Text(if (tab == 2) "தொலைபேசி எண்" else "விவரங்கள்") },
                    minLines = 3
                )
                OutlinedTextField(
                    value = extra, onValueChange = { extra = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(when (tab) { 0 -> "இடம் / மாவட்டம்"; 1 -> "கடைசி தேதி (விருப்பம்)"; else -> "குறிப்புகள் (விருப்பம்)" }) },
                    singleLine = true
                )
                Button(
                    enabled = !busy,
                    onClick = {
                        if (title.isBlank()) {
                            message = "தலைப்பு அல்லது பெயரை உள்ளிடுங்கள்"
                        } else {
                            val savedTitle = title.trim()
                            val savedDetails = details.trim()
                            val savedExtra = extra.trim()
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
                                        message = "சாதனத்தில் சேமிக்கப்பட்டது; Cloud சேமிப்பு தோல்வி: ${cloudError.message.orEmpty().take(100)}"
                                    }
                                    title = ""; details = ""; extra = ""
                                    page = Page.LIST
                                    refreshLocal()
                                } catch (e: Exception) {
                                    message = "சேமிக்க முடியவில்லை: ${e.message.orEmpty().take(120)}"
                                } finally { busy = false }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "சேமிக்கிறது…" else "சேமிக்கவும்") }
                OutlinedButton(onClick = { page = Page.LIST }, modifier = Modifier.fillMaxWidth()) { Text("ரத்து செய்") }
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
