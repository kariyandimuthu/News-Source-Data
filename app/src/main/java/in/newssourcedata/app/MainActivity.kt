package `in`.newssourcedata.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { App() } } }
}
private data class Entry(val id:String,val title:String,val body:String,val meta:String="")
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App() {
 val context= LocalContext.current
 val api= remember { SupabaseClient(context.applicationContext) }
 val scope= rememberCoroutineScope()
 var signed by remember { mutableStateOf(api.isSignedIn) }
 var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var signup by remember { mutableStateOf(false) }
 var tab by remember { mutableIntStateOf(0) }; var form by remember { mutableStateOf(false) }
 var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }; var district by remember { mutableStateOf("Tirunelveli") }; var category by remember { mutableStateOf("general") }
 var rows by remember { mutableStateOf(listOf<Entry>()) }; var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
 fun load() { scope.launch { busy=true; try { rows=withContext(Dispatchers.IO) { val table=listOf("news_items","assignments","contacts")[tab]; val a=api.list(table); (0 until a.length()).map { i -> val o=a.getJSONObject(i); when(tab) { 0->Entry(o.optString("id"),o.optString("headline"),o.optString("body"),o.optString("category")); 1->Entry(o.optString("id"),o.optString("title"),o.optString("description"),o.optString("status")); else->Entry(o.optString("id"),o.optString("full_name"),o.optString("phone"),o.optString("organization")) } } }; message="" } catch(e:Exception){ message=e.message ?: "தரவைப் பெற முடியவில்லை" }; busy=false } }
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
 Scaffold(topBar={TopAppBar(title={Text("News Source Data")},actions={TextButton(onClick={api.signOut();signed=false}){Text("வெளியேறு")}})},bottomBar={NavigationBar{listOf("செய்திகள்","அசைன்மென்ட்","தொடர்புகள்").forEachIndexed{i,l->NavigationBarItem(selected=tab==i,onClick={tab=i;form=false},icon={Text(listOf("📰","📋","☎")[i])},label={Text(l)})}}},floatingActionButton={if(!form) FloatingActionButton(onClick={title="";body="";form=true}){Text("+")}}) { p ->
  Column(Modifier.fillMaxSize().padding(p).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
   if(form) {
    Text(listOf("புதிய செய்தி","புதிய அசைன்மென்ட்","புதிய தொடர்பு")[tab],style=MaterialTheme.typography.headlineSmall)
    OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text(if(tab==2)"பெயர்" else "தலைப்பு")},singleLine=true)
    OutlinedTextField(body,{body=it},Modifier.fillMaxWidth(),label={Text(if(tab==2)"தொலைபேசி எண்" else "முழு விவரம் / குறிப்புகள்")},minLines=4)
    if(tab!=2){ Text("மாவட்டம்"); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(district=="Tirunelveli",{district="Tirunelveli"},label={Text("திருநெல்வேலி")});FilterChip(district=="Tenkasi",{district="Tenkasi"},label={Text("தென்காசி")})} }
    if(tab!=2) OutlinedTextField(category,{category=it},Modifier.fillMaxWidth(),label={Text("வகை (crime / politics / general)")},singleLine=true)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={form=false},modifier=Modifier.weight(1f)){Text("ரத்து")}; Button(enabled=!busy&&title.isNotBlank(),onClick={scope.launch { busy=true; try { withContext(Dispatchers.IO){ val did=if(district=="Tenkasi") "6b4aa1b1-3d0c-42c1-ac3b-06f7e123531b" else "0288efc9-030c-4faa-b110-64a318fcc077"; val o=when(tab){0->JSONObject().put("headline",title.trim()).put("body",body.trim()).put("category",category.ifBlank{"general"}).put("district_id",did);1->JSONObject().put("title",title.trim()).put("description",body.trim()).put("category",category.ifBlank{"general"}).put("district_id",did);else->JSONObject().put("full_name",title.trim()).put("phone",body.trim())};api.insert(listOf("news_items","assignments","contacts")[tab],o) };form=false;message="Supabase-ல் சேமிக்கப்பட்டது";load() } catch(e:Exception){message=e.message ?: "சேமிக்க முடியவில்லை"};busy=false }},modifier=Modifier.weight(1f)){Text(if(busy)"சேமிக்கிறது…" else "சேமி")} }
   } else {
    Text(listOf("செய்திகள்","அசைன்மென்ட்","தொடர்புகள்")[tab],style=MaterialTheme.typography.headlineSmall)
    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(message.isNotBlank()) Text(message,color=if(message.contains("சேமிக்கப்பட்டது")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
    if(rows.isEmpty()&&!busy) Text("பதிவுகள் இல்லை. + பொத்தானைத் தொட்டு சேர்க்கவும்.")
    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(rows,key={it.id}){e->ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(e.title,style=MaterialTheme.typography.titleMedium);if(e.body.isNotBlank())Text(e.body);if(e.meta.isNotBlank())Text(e.meta,style=MaterialTheme.typography.labelSmall);if(tab!=2) OutlinedButton(onClick={val s=(if(tab==0)"📰 செய்தி" else "📋 அசைன்மென்ட்")+"\n\n"+e.title+"\n"+e.body;context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,s),"பகிரவும்"))},modifier=Modifier.align(Alignment.End)){Text("பகிர்")}}}}}
   }
  }
 }
}
