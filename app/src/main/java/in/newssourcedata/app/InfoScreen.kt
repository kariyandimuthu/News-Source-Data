package `in`.newssourcedata.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InfoScreen(api: SupabaseApi, onBack: () -> Unit) {
    var entries by remember { mutableStateOf<List<InfoEntryRecord>>(emptyList()) }
    var searchText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<InfoEntryRecord?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reloadEntries() {
        loading = true
        try {
            entries = api.getInfoEntries()
            message = ""
        } catch (error: Exception) {
            message = error.message ?: "Info விவரங்களைப் பெற முடியவில்லை."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { reloadEntries() }
    BackHandler { onBack() }

    val filtered = remember(entries, searchText) {
        val q = searchText.trim()
        if (q.isBlank()) entries
        else entries.filter {
            it.label.contains(q, ignoreCase = true) ||
                it.value.contains(q, ignoreCase = true) ||
                it.groupName.contains(q, ignoreCase = true)
        }
    }

    if (showEditor) {
        InfoEntryEditorDialog(
            initial = editingEntry,
            onDismiss = { showEditor = false; editingEntry = null },
            onSave = { label, value, group ->
                api.saveInfoEntry(editingEntry?.id, label, value, group)
                showEditor = false
                editingEntry = null
                reloadEntries()
                message = "Info விவரம் சேமிக்கப்பட்டது."
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Info", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("விவரங்கள்  ·  புள்ளி விவரம்", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text("‹", fontSize = 30.sp)
                    }
                },
                actions = {
                    TextButton(onClick = { editingEntry = null; showEditor = true }) { Text("＋ சேர்") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "தேடு", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp))
                    BasicTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { inner ->
                            Box {
                                if (searchText.isBlank()) Text("காவல் நிலையம், வழக்கு எண், கொலை எண்ணிக்கை…",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                inner()
                            }
                        }
                    )
                    if (searchText.isNotBlank()) {
                        TextButton(onClick = { searchText = "" }, contentPadding = PaddingValues(0.dp)) { Text("×") }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("விவரங்கள்", modifier = Modifier.weight(1.15f),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("புள்ளி விவரம்", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(38.dp))
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            if (loading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f).padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchText.isBlank()) "இன்னும் Info விவரங்கள் சேர்க்கப்படவில்லை. “＋ சேர்” மூலம் காவல் நிலையம், கடைசி வழக்கு எண் அல்லது புள்ளி விவரங்களைச் சேர்க்கவும்."
                        else "இந்தத் தேடலுக்குப் பொருத்தமான விவரம் இல்லை.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { editingEntry = entry; showEditor = true }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(entry.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (entry.groupName.isNotBlank()) {
                                    Text(entry.groupName, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                entry.value.ifBlank { "—" },
                                modifier = Modifier.weight(1f).padding(end = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (entry.value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { editingEntry = entry; showEditor = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "விவரத்தைத் திருத்து",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                    }
                }
            }

            if (message.isNotBlank()) {
                Text(message, style = MaterialTheme.typography.bodySmall,
                    color = if (message.contains("சேமிக்கப்பட்டது")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun InfoEntryEditorDialog(
    initial: InfoEntryRecord?,
    onDismiss: () -> Unit,
    onSave: suspend (String, String, String) -> Unit
) {
    var label by remember(initial) { mutableStateOf(initial?.label.orEmpty()) }
    var value by remember(initial) { mutableStateOf(initial?.value.orEmpty()) }
    var group by remember(initial) { mutableStateOf(initial?.groupName.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (initial == null) "Info விவரம் சேர்க்க" else "Info விவரத்தைத் திருத்து") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("விவரங்கள்") }, placeholder = { Text("எ.கா. மேலப்பாளையம் காவல் நிலையம்") },
                    singleLine = false, minLines = 2
                )
                OutlinedTextField(
                    value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("புள்ளி விவரம்") }, placeholder = { Text("எ.கா. கடைசி வழக்கு எண் / எண்ணிக்கை") },
                    singleLine = false, minLines = 2
                )
                OutlinedTextField(
                    value = group, onValueChange = { group = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("பிரிவு (விருப்பம்)") }, placeholder = { Text("காவல் நிலையங்கள் / குற்றப் புள்ளிவிவரம்") },
                    singleLine = true
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isBlank()) {
                        error = "விவரங்கள் புலத்தை நிரப்பவும்."
                    } else {
                        scope.launch {
                            saving = true
                            error = ""
                            try {
                                onSave(label.trim(), value.trim(), group.trim())
                            } catch (exception: Exception) {
                                error = exception.message ?: "சேமிக்க முடியவில்லை. மீண்டும் முயற்சிக்கவும்."
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
                enabled = !saving,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("சேமி")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("ரத்து") }
        }
    )
}
