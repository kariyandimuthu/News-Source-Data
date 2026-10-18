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
import kotlinx.coroutines.launch

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

@Composable
fun NewsSourceScreen(dao: NewsDao) {
    var tab by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(listOf<String>()) }
    var message by remember { mutableStateOf("உள்ளூர் தரவுத்தளம் தயார்") }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            items = when (tab) {
                0 -> dao.getNews().map { "${it.title} — ${it.district}\n${it.details}" }
                1 -> dao.getAssignments().map { "${it.title}\n${it.details} ${it.dueDate}" }
                else -> dao.getContacts().map { "${it.name} — ${it.phone}\n${it.notes}" }
            }
        }
    }

    LaunchedEffect(tab) {
        title = ""
        details = ""
        extra = ""
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("News Source Data") })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("📰") },
                    label = { Text("செய்திகள்") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("📋") },
                    label = { Text("அசைன்மென்ட்") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Text("☎") },
                    label = { Text("தொடர்புகள்") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                when (tab) {
                    0 -> "புதிய செய்தி சேர்க்க"
                    1 -> "புதிய அசைன்மென்ட் சேர்க்க"
                    else -> "புதிய தொடர்பு சேர்க்க"
                },
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(if (tab == 2) "பெயர்" else "தலைப்பு")
                },
                singleLine = true
            )

            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(if (tab == 2) "தொலைபேசி எண்" else "விவரங்கள்")
                }
            )

            OutlinedTextField(
                value = extra,
                onValueChange = { extra = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        when (tab) {
                            0 -> "மாவட்டம்"
                            1 -> "கடைசி தேதி (விருப்பம்)"
                            else -> "குறிப்புகள் (விருப்பம்)"
                        }
                    )
                },
                singleLine = true
            )

            Button(
                onClick = {
                    if (title.isBlank()) {
                        message = "தலைப்பு அல்லது பெயரை உள்ளிடுங்கள்"
                    } else {
                        scope.launch {
                            when (tab) {
                                0 -> dao.insertNews(
                                    NewsItem(
                                        title = title.trim(),
                                        details = details.trim(),
                                        district = extra.trim()
                                    )
                                )
                                1 -> dao.insertAssignment(
                                    Assignment(
                                        title = title.trim(),
                                        details = details.trim(),
                                        dueDate = extra.trim()
                                    )
                                )
                                else -> dao.insertContact(
                                    Contact(
                                        name = title.trim(),
                                        phone = details.trim(),
                                        notes = extra.trim()
                                    )
                                )
                            }
                            title = ""
                            details = ""
                            extra = ""
                            message = "சாதனத்தில் சேமிக்கப்பட்டது"
                            refresh()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("சேமிக்கவும்")
            }

            Text(message, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("சேமிக்கப்பட்ட பதிவுகள்", style = MaterialTheme.typography.titleMedium)

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { entry ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = entry,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
