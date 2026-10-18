package in.newssourcedata.app

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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = NewsDatabase.get(this).dao()
        setContent {
            MaterialTheme {
                NewsSourceApp(dao)
            }
        }
    }
}

@Composable
fun NewsSourceApp(dao: NewsDao) {
    var section by remember { mutableStateOf("செய்திகள்") }
    var showForm by remember { mutableStateOf(false) }
    var headline by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val news by dao.observeNews().collectAsState(initial = emptyList())
    val assignments by dao.observeAssignments().collectAsState(initial = emptyList())
    val contacts by dao.observeContacts().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBarCompat(title = "News Source Data")
        },
        bottomBar = {
            NavigationBar {
                listOf("செய்திகள்", "அசைன்மென்ட்", "தொடர்புகள்").forEach { item ->
                    NavigationBarItem(
                        selected = section == item,
                        onClick = { section = item; showForm = false },
                        icon = {},
                        label = { Text(item) }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                showForm = true
                headline = ""; body = ""; title = ""; notes = ""; name = ""; phone = ""
            }) { Text("+") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "உள்ளூரில் சேமிக்கப்படும் தரவுகள் • இணையம் தேவையில்லை",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            if (showForm) {
                Text(section, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                when (section) {
                    "செய்திகள்" -> {
                        OutlinedTextField(headline, { headline = it }, label = { Text("செய்தித் தலைப்பு") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(body, { body = it }, label = { Text("செய்தி விவரம்") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    }
                    "அசைன்மென்ட்" -> {
                        OutlinedTextField(title, { title = it }, label = { Text("அசைன்மென்ட்") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(notes, { notes = it }, label = { Text("குறிப்புகள்") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    }
                    else -> {
                        OutlinedTextField(name, { name = it }, label = { Text("பெயர்") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(phone, { phone = it }, label = { Text("தொலைபேசி") }, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            when (section) {
                                "செய்திகள்" -> if (headline.isNotBlank()) dao.insertNews(NewsItem(headline = headline.trim(), body = body.trim()))
                                "அசைன்மென்ட்" -> if (title.isNotBlank()) dao.insertAssignment(AssignmentItem(title = title.trim(), notes = notes.trim()))
                                else -> if (name.isNotBlank()) dao.insertContact(ContactItem(name = name.trim(), phone = phone.trim()))
                            }
                            showForm = false
                        }
                    }) { Text("சேமி") }
                    OutlinedButton(onClick = { showForm = false }) { Text("ரத்து") }
                }
            } else {
                when (section) {
                    "செய்திகள்" -> {
                        if (news.isEmpty()) EmptyMessage("இன்னும் செய்திகள் இல்லை. + அழுத்தி புதிய செய்தியைச் சேர்க்கவும்.")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(news, key = { it.id }) { item ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(item.headline, style = MaterialTheme.typography.titleMedium)
                                        if (item.body.isNotBlank()) Text(item.body)
                                    }
                                }
                            }
                        }
                    }
                    "அசைன்மென்ட்" -> {
                        if (assignments.isEmpty()) EmptyMessage("இன்னும் அசைன்மென்ட்கள் இல்லை.")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(assignments, key = { it.id }) { item ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                                        if (item.notes.isNotBlank()) Text(item.notes)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        if (contacts.isEmpty()) EmptyMessage("இன்னும் தொடர்புகள் இல்லை.")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(contacts, key = { it.id }) { item ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                                        if (item.phone.isNotBlank()) Text(item.phone)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("AI வசதி — அடுத்த கட்டம்", style = MaterialTheme.typography.titleSmall)
                        Text("உள்ளூர் AI மாதிரி இன்னும் இணைக்கப்படவில்லை. இந்த தொடக்கப் பதிப்பு இணையமின்றி தரவுகளைச் சேமிக்கிறது.")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBarCompat(title: String) {
    TopAppBar(title = { Text(title) })
}

@Composable
private fun EmptyMessage(message: String) {
    Text(message, style = MaterialTheme.typography.bodyMedium)
}
