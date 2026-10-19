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
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val SUPABASE_URL = "https://cmvvcwcavopmpwkowhnz.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_iY6fpaaFMuRpBpShDVSCtA_OeRglNDw"

private val supabase = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_KEY
) {
    install(Auth)
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = NewsDatabase.getInstance(applicationContext).newsDao()
        setContent {
            MaterialTheme {
                NewsSourceScreen(dao)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsSourceScreen(dao: NewsDao) {
    var tab by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(listOf<String>()) }
    var message by remember { mutableStateOf("Supabase இணைப்பைத் தொடங்குகிறது…") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun ensureSignedIn() {
        if (supabase.gotrue.currentSessionOrNull() == null) {
            supabase.gotrue.signInAnonymously()
        }
    }

    suspend fun refreshLocal() {
        entries = when (tab) {
            0 -> dao.getNews().map { "${it.title} — ${it.district}\n${it.details}" }
            1 -> dao.getAssignments().map { "${it.title}\n${it.details} ${it.dueDate}" }
            else -> dao.getContacts().map { "${it.name} — ${it.phone}\n${it.notes}" }
        }
    }

    suspend fun refreshCloud() {
        ensureSignedIn()
        entries = when (tab) {
            0 -> supabase.from("news_items").select().decodeList<CloudNews>()
                .map { "${it.headline} — ${it.locationText.orEmpty()}\n${it.body.orEmpty()}" }
            1 -> supabase.from("assignments").select().decodeList<CloudAssignment>()
                .map { "${it.title}\n${it.description.orEmpty()} ${it.dueAt.orEmpty()}" }
            else -> supabase.from("contacts").select().decodeList<CloudContact>()
                .map { "${it.fullName} — ${it.phone.orEmpty()}\n${it.notes.orEmpty()}" }
        }
    }

    LaunchedEffect(Unit) {
        try {
            ensureSignedIn()
            message = "Supabase இணைக்கப்பட்டது"
            refreshCloud()
        } catch (e: Exception) {
            message = "Cloud இணைப்பு தோல்வி; உள்ளூர் சேமிப்பு கிடைக்கும். ${e.message.orEmpty().take(120)}"
            refreshLocal()
        }
    }

    LaunchedEffect(tab) {
        title = ""
        details = ""
        extra = ""
        try {
            refreshCloud()
            message = "Supabase தரவு ஏற்றப்பட்டது"
        } catch (e: Exception) {
            refreshLocal()
            message = "உள்ளூர் தரவு காட்டப்படுகிறது; Cloud: ${e.message.orEmpty().take(100)}"
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("News Source Data") }) },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                when (tab) { 0 -> "புதிய செய்தி சேர்க்க"; 1 -> "புதிய அசைன்மென்ட் சேர்க்க"; else -> "புதிய தொடர்பு சேர்க்க" },
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (tab == 2) "பெயர்" else "தலைப்பு") },
                singleLine = true
            )
            OutlinedTextField(
                value = details, onValueChange = { details = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (tab == 2) "தொலைபேசி எண்" else "விவரங்கள்") }
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
                                    0 -> {
                                        dao.insertNews(NewsItem(title = savedTitle, details = savedDetails, district = savedExtra))
                                        ensureSignedIn()
                                        supabase.from("news_items").insert(CloudNewsInsert(savedTitle, savedDetails, savedExtra.ifBlank { null }))
                                    }
                                    1 -> {
                                        dao.insertAssignment(Assignment(title = savedTitle, details = savedDetails, dueDate = savedExtra))
                                        ensureSignedIn()
                                        supabase.from("assignments").insert(CloudAssignmentInsert(savedTitle, savedDetails, savedExtra.ifBlank { null }))
                                    }
                                    else -> {
                                        dao.insertContact(Contact(name = savedTitle, phone = savedDetails, notes = savedExtra))
                                        ensureSignedIn()
                                        supabase.from("contacts").insert(CloudContactInsert(savedTitle, savedDetails, savedExtra))
                                    }
                                }
                                title = ""; details = ""; extra = ""
                                message = "சாதனத்திலும் Supabase Cloud-லும் சேமிக்கப்பட்டது"
                                refreshCloud()
                            } catch (e: Exception) {
                                message = "உள்ளூர் சேமிப்பு நடந்திருக்கலாம்; Cloud பிழை: ${e.message.orEmpty().take(150)}"
                                refreshLocal()
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "சேமிக்கிறது…" else "சேமிக்கவும்") }

            OutlinedButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            refreshCloud()
                            message = "Supabase தரவு புதுப்பிக்கப்பட்டது"
                        } catch (e: Exception) {
                            message = "Cloud புதுப்பிப்பு தோல்வி: ${e.message.orEmpty().take(120)}"
                            refreshLocal()
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cloud தரவைப் புதுப்பிக்க") }

            Text(message, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("சேமிக்கப்பட்ட பதிவுகள்", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries) { entry ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Text(text = entry, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}
