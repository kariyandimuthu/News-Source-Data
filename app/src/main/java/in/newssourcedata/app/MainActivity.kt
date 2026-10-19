package `in`.newssourcedata.app

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { App() } } }
}
private data class Entry(val id:String,val title:String,val body:String,val meta:String="",val date:String="")
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App() {
 val context= LocalContext.current
 val api= remember { SupabaseClient(context.applicationContext) }
 val scope= rememberCoroutineScope()
 var signed by remember { mutableStateOf(api.isSignedIn) }
 var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var signup by remember { mutableStateOf(false) }
 var tab by remember { mutableIntStateOf(0) }; var form by remember { mutableStateOf(false) }
 var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }
 var district by remember { mutableStateOf("Tirunelveli") }; var category by remember { mutableStateOf("general") }
 var eventDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
 var eventTime by remember { mutableStateOf("") }; var eventPlace by remember { mutableStateOf("") }
 var contactExtra by remember { mutableStateOf("") }; var search by remember { mutableStateOf("") }
 var rows by remember { mutableStateOf(listOf<Entry>()) }; var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
 fun load() { scope.launch { busy=true; try { rows=withContext(Dispatchers.IO) { val table=listOf("news_items","assignments","contacts")[tab]; val a=api.list(table); (0 until a.length()).map { i -> val o=a.getJSONObject(i); when(tab) { 0->Entry(o.optString("id"),o.optString("headline"),o.optString("body"),o.optString("category"),o.optString("news_date")); 1-> { val d=o.optString("description"); val date=Regex("\\[DATE:([^]]+)]").find(d)?.groupValues?.get(1) ?: ""; Entry(o.optString("id"),o.optString("title"),d.replace(Regex("\\[DATE:[^]]+]\\s*"),"").replace(Regex("\\[TIME:[^]]+]\\s*"),"").replace(Regex("\\[PLACE:[^]]+]\\s*"),""),o.optString("category"),date) }; else->Entry(o.optString("id"),o.optString("full_name"),o.optString("phone"),o.optString("notes")) } } }.sortedWith(compareBy<Entry> { if(tab==1) it.date.ifBlank{"9999-99-99"} else "" }.thenByDescending { it.id }); message="" } catch(e:Exception){ message=e.message ?: "தரவைப் பெற முடியவில்லை" }; busy=false } }
 fun clearForm() { title=""; body=""; category="general"; eventDate=SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()); eventTime=""; eventPlace=""; contactExtra="" }
 LaunchedEffect(signed,tab) { if(signed) load() }
 if(!signed) {
  Scaffold(topBar={TopAppBar(title={Text("News Source Data")})}) { p-> Column(Modifier.fillMaxSize().padding(p).padding(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text(if(signup) "புதிய கணக்கு உருவாக்கு" else "உள்நுழைக", style=MaterialTheme.typography.headlineSmall)
   OutlinedTextField(email,{email=it},Modifier.fillMaxWidth(),label={Text("மின்னஞ்சல்")},singleLine=true)
   OutlinedTextField(password,{password=it},Modifier.fillMaxWidth(),label={Text("கடவுச்சொல் (குறைந்தது 6 எழுத்துகள்)")},singleLine=true)
   Button(enabled=!busy && email.contains("@") && password.length>=6,onClick={scope.launch { busy=true; message=""; try { withContext(Dispatchers.IO) { if(signup) api.signUp(email,password) else api.signIn(email,password) }; signed=api.isSignedIn; if(!signed) message="கணக்கு உருவாக்கப்பட்டது. மின்னஞ்சலை உறுதிப்படுத்தி உள்நுழையவும்." } catch(e:Exception){message=e.message ?: "தோல்வி"}; busy=false }},modifier=Modifier.fillMaxWidth()){Text(if(busy) "செயல்படுகிறது…" else if(signup) "கணக்கு உருவாக்கு" else "உள்நுழை")}
   TextButton(onClick={signup=!signup;message=""}){Text(if(signup) "ஏற்கெனவே கணக்கு உள்ளதா? உள்நுழைக" else "புதிய கணக்கு உருவாக்கவும்")}
   if(message.isNotBlank()) Text(message,color=MaterialTheme.colorScheme.error)
  } }
  return
 }
 Scaffold(
  topBar={TopAppBar(title={Text("News Source Data")},actions={TextButton(onClick={api.signOut();signed=false}){Text("வெளியேறு")}})},
  bottomBar={if(form) {
   Surface(tonalElevation=3.dp) { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
    OutlinedButton(onClick={form=false},modifier=Modifier.weight(1f).heightIn(min=52.dp)){Text("ரத்து")}
    Button(enabled=!busy&&title.isNotBlank(),onClick={scope.launch {
     busy=true; message=""
     try {
      withContext(Dispatchers.IO) {
       val did=if(district=="Tenkasi") "6b4aa1b1-3d0c-42c1-ac3b-06f7e123531b" else "0288efc9-030c-4faa-b110-64a318fcc077"
       val o=when(tab) {
        0->JSONObject().put("headline",title.trim()).put("body",body.trim()).put("category",category.ifBlank{"general"}).put("district_id",did)
        1->JSONObject().put("title",title.trim()).put("description","[DATE:$eventDate] [TIME:$eventTime] [PLACE:$eventPlace]\n"+body.trim()).put("category",category.ifBlank{"general"}).put("district_id",did)
        else->JSONObject().put("full_name",title.trim()).put("phone",body.trim()).put("notes",contactExtra.trim())
       }
       api.insert(listOf("news_items","assignments","contacts")[tab],o)
      }
      form=false; message="சேமிக்கப்பட்டது"; load()
     } catch(e:Exception){message=e.message ?: "சேமிக்க முடியவில்லை"}
     busy=false
    } },modifier=Modifier.weight(2f).heightIn(min=52.dp)){Text(if(busy)"சேமிக்கிறது…" else "சேமி")}
   } }
  } else {
   NavigationBar { listOf("செய்திகள்","அசைன்மென்ட்","தொடர்புகள்").forEachIndexed{i,l->NavigationBarItem(selected=tab==i,onClick={tab=i;form=false;search=""},icon={Text(listOf("📰","📋","☎")[i])},label={Text(l)})} }
  }},
  floatingActionButton={if(!form) FloatingActionButton(onClick={clearForm();form=true}){Text("+")}}
 ) { p ->
  Column(Modifier.fillMaxSize().padding(p).padding(horizontal=16.dp, vertical=10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
   if(form) {
    Text(listOf("புதிய செய்தி","புதிய அசைன்மென்ட்","புதிய தொடர்பு")[tab],style=MaterialTheme.typography.headlineSmall)
    when(tab) {
     0 -> {
      OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("செய்தித் தலைப்பு")},singleLine=true)
      OutlinedTextField(body,{body=it},Modifier.weight(1f,fill=false).fillMaxWidth().heightIn(min=280.dp),label={Text("முழுச் செய்தியை இங்கே எழுதுங்கள்")},minLines=12,maxLines=100)
      Text("கூடுதல் விவரங்கள்",style=MaterialTheme.typography.titleMedium)
      OutlinedTextField(category,{category=it},Modifier.fillMaxWidth(),label={Text("செய்தி வகை")},singleLine=true)
      Text("மாவட்டம்")
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(district=="Tirunelveli",{district="Tirunelveli"},label={Text("திருநெல்வேலி")});FilterChip(district=="Tenkasi",{district="Tenkasi"},label={Text("தென்காசி")})}
     }
     1 -> {
      OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("நிகழ்ச்சி / அசைன்மென்ட் தலைப்பு")},singleLine=true)
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
       OutlinedTextField(eventDate,{eventDate=it},Modifier.weight(1f),label={Text("தேதி (YYYY-MM-DD)")},singleLine=true)
       OutlinedTextField(eventTime,{eventTime=it},Modifier.weight(1f),label={Text("நேரம்")},singleLine=true)
      }
      OutlinedTextField(eventPlace,{eventPlace=it},Modifier.fillMaxWidth(),label={Text("இடம்")},singleLine=true)
      OutlinedTextField(body,{body=it},Modifier.fillMaxWidth().weight(1f,fill=false).heightIn(min=180.dp),label={Text("முழு நிகழ்ச்சி விவரங்கள் / செய்ய வேண்டியவை")},minLines=8,maxLines=100)
      OutlinedTextField(category,{category=it},Modifier.fillMaxWidth(),label={Text("வகை")},singleLine=true)
      Text("மாவட்டம்")
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(district=="Tirunelveli",{district="Tirunelveli"},label={Text("திருநெல்வேலி")});FilterChip(district=="Tenkasi",{district="Tenkasi"},label={Text("தென்காசி")})}
     }
     else -> {
      OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("முழுப் பெயர்")},singleLine=true)
      OutlinedTextField(body,{body=it},Modifier.fillMaxWidth(),label={Text("மொபைல் எண்")},singleLine=true)
      OutlinedTextField(contactExtra,{contactExtra=it},Modifier.fillMaxWidth().heightIn(min=120.dp),label={Text("நிறுவனம் / பதவி / குறிப்புகள்")},minLines=4)
     }
    }
    if(message.isNotBlank()) Text(message,color=MaterialTheme.colorScheme.error)
   } else {
    Text(listOf("செய்திகள்","அசைன்மென்ட்","தொடர்புகள்")[tab],style=MaterialTheme.typography.headlineSmall)
    OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text("தேடுங்கள்")},singleLine=true)
    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(message.isNotBlank()) Text(message,color=if(message.contains("சேமிக்கப்பட்டது")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
    val visibleRows=rows.filter { it.title.contains(search,true)||it.body.contains(search,true)||it.meta.contains(search,true) }
    if(visibleRows.isEmpty()&&!busy) Text(if(tab==1)"அசைன்மென்ட் இல்லை. + மூலம் நிகழ்ச்சியைச் சேர்க்கவும்." else "பதிவுகள் இல்லை. + பொத்தானைத் தொட்டு சேர்க்கவும்.")
    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=12.dp)){items(visibleRows,key={it.id}){e->ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
     if(tab==1 && e.date.isNotBlank()) Text(e.date,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
     Text(e.title,style=MaterialTheme.typography.titleMedium)
     if(e.body.isNotBlank())Text(e.body)
     if(e.meta.isNotBlank())Text(e.meta,style=MaterialTheme.typography.labelSmall)
     if(tab!=2) OutlinedButton(onClick={val s=(if(tab==0)"📰 செய்தி" else "📋 அசைன்மென்ட்")+"\n\n"+e.title+"\n"+e.body;context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,s),"பகிரவும்"))},modifier=Modifier.align(Alignment.End)){Text("பகிர்")}
    }}}}
   }
  }
 }
}
