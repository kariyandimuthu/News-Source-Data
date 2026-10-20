package `in`.newssourcedata.app

import android.content.Intent
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.MediaStore
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Brush
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
private data class Entry(val id:String,val title:String,val body:String,val meta:String="",val date:String="",val phone:String="",val startTime:String="",val endDate:String="",val endTime:String="")
private val departments = listOf("காவல்","கோயில்","காலநிலை","கல்வி","மருத்துவம்","அரசியல்","உள்ளாட்சி","விபத்து","பொது")
private val categories = listOf("குற்றம்","விபத்து","செய்தியாளர் சந்திப்பு","அரசு அறிவிப்பு","நிகழ்ச்சி","மக்கள் பிரச்சினை","மற்றவை")
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App() {
 val context=LocalContext.current
 val api= remember { SupabaseClient(context.applicationContext) }
 val scope= rememberCoroutineScope()
 var signed by remember { mutableStateOf(api.isSignedIn) }
 var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var signup by remember { mutableStateOf(false) }
 var tab by remember { mutableIntStateOf(0) }; var form by remember { mutableStateOf(false) }; var formStep by remember { mutableIntStateOf(0) }
 var editingId by remember { mutableStateOf<String?>(null) }; var showAddMenu by remember { mutableStateOf(false) }
 var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }
 var district by remember { mutableStateOf("திருநெல்வேலி") }; var department by remember { mutableStateOf("காவல்") }; var category by remember { mutableStateOf("மற்றவை") }; var customCategory by remember { mutableStateOf("") }; var userCategories by remember { mutableStateOf(listOf<String>()) }
 var eventDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
 var eventTime by remember { mutableStateOf("") }; var eventEndTime by remember { mutableStateOf("") }; var eventEndDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }; var eventPlace by remember { mutableStateOf("") }
 var contactExtra by remember { mutableStateOf("") }; var search by remember { mutableStateOf("") }; var selectedDate by remember { mutableStateOf("") }; var selectedCategory by remember { mutableStateOf("அனைத்தும்") }
 var calendarMonth by remember { mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)) }; var calendarYear by remember { mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) }; var calendarDay by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
 var rows by remember { mutableStateOf(listOf<Entry>()) }; var page by remember { mutableIntStateOf(0) }; var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
 var selectedEntry by remember { mutableStateOf<Entry?>(null) }; var deleteTarget by remember { mutableStateOf<Entry?>(null) }
 var sharedDraft by remember { mutableStateOf((context as? Activity)?.intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()) }
 fun load() { scope.launch { busy=true; try { rows=withContext(Dispatchers.IO) { val table=listOf("news_items","assignments","contacts")[tab]; val a=api.list(table); (0 until a.length()).mapNotNull { i -> val o=a.getJSONObject(i); when(tab) { 0->Entry(o.optString("id"),o.optString("headline"),o.optString("body"),o.optString("category"),o.optString("news_date")); 1->{ val d=o.optString("description"); val date=Regex("\\[DATE:([^]]+)]").find(d)?.groupValues?.get(1) ?: ""; val startTime=Regex("\\[STARTTIME:([^]]*)]").find(d)?.groupValues?.get(1) ?: Regex("\\[TIME:([^]]*)]").find(d)?.groupValues?.get(1) ?: ""; val endDate=Regex("\\[ENDDATE:([^]]*)]").find(d)?.groupValues?.get(1) ?: date; val endTime=Regex("\\[ENDTIME:([^]]*)]").find(d)?.groupValues?.get(1) ?: ""; val expired=try { if(endDate.isNotBlank()&&endTime.isNotBlank()) { val end=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).parse("$endDate $endTime"); end!=null && end.before(Date()) } else false } catch(_:Exception){false}; val id=o.optString("id"); if(expired) { try { api.delete("assignments",id) } catch(_:Exception) { }; null } else { val clean=d.replace(Regex("\\[(DATE|TIME|STARTTIME|ENDDATE|ENDTIME|PLACE):[^]]*]\\s*"),"").replace(Regex("\\\\+n"),"\n").replace("\\n","\n").trim(); Entry(id,o.optString("title"),clean,o.optString("category"),date,"",startTime,endDate,endTime) } }; else->Entry(o.optString("id"),o.optString("full_name"),o.optString("phone"),o.optString("notes"),"",o.optString("phone")) } } }.sortedWith(compareByDescending<Entry> { it.date.ifBlank{"0000-00-00"} }.thenByDescending { it.id }); message="" } catch(e:Exception){ message=e.message ?: "தரவைப் பெற முடியவில்லை" }; busy=false } }
 fun clearForm() { editingId=null; title=""; body=""; category="மற்றவை"; customCategory=""; department="காவல்"; district="திருநெல்வேலி"; eventDate=SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()); eventTime=""; eventEndTime=""; eventEndDate=eventDate; eventPlace=""; contactExtra=""; formStep=0 }
 fun startEntry(which:Int) { tab=which; clearForm(); form=true; showAddMenu=false; message="" }
 fun saveEntry() { scope.launch { busy=true; message=""; try { withContext(Dispatchers.IO) {
   val did=if(district=="தென்காசி") "6b4aa1b1-3d0c-42c1-ac3b-06f7e123531b" else "0288efc9-030c-4faa-b110-64a318fcc077"
   val o=when(tab) {
    0 -> { val headline=body.lineSequence().firstOrNull()?.trim()?.take(90).orEmpty().ifBlank{"தலைப்பிடப்படாத செய்தி"}; JSONObject().put("headline",headline).put("body",body.trim()).put("category","$department • $category").put("district_id",did) }
    1 -> { val generatedTitle=body.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank{"நிகழ்ச்சி அசைன்மென்ட்"}; JSONObject().put("title",generatedTitle).put("description","[DATE:$eventDate] [STARTTIME:$eventTime] [ENDDATE:$eventEndDate] [ENDTIME:$eventEndTime] [PLACE:$eventPlace]\n"+body.trim()).put("category",department).put("district_id",did) }
    else -> JSONObject().put("full_name",title.trim()).put("phone",body.trim()).put("notes",contactExtra.trim())
   }
   if(editingId!=null) api.update(listOf("news_items","assignments","contacts")[tab],editingId!!,o) else api.insert(listOf("news_items","assignments","contacts")[tab],o)
  }; form=false; formStep=0; message="சேமிக்கப்பட்டது"; load()
 } catch(e:Exception){message=e.message ?: "சேமிக்க முடியவில்லை"}; busy=false } }
 LaunchedEffect(signed,tab) { if(signed) load() }
 LaunchedEffect(sharedDraft) { if(sharedDraft.isBlank()) Unit }
 val cameraLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
  val bitmap=result.data?.extras?.get("data") as? Bitmap
  if(bitmap!=null) { busy=true; TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener { vision -> val extracted=vision.text.trim(); if(extracted.isNotBlank()) body=if(body.isBlank()) extracted else body+"\\n"+extracted; message=if(extracted.isBlank()) "படத்தில் வாசிக்கக்கூடிய உரை கிடைக்கவில்லை. தெளிவான படத்தை எடுக்கவும்." else "படத்திலிருந்து உரை சேர்க்கப்பட்டது"; busy=false }.addOnFailureListener { message="OCR வாசிப்பு தோல்வியடைந்தது"; busy=false } }
 }
 if(!signed) {
  Scaffold(topBar={TopAppBar(title={Text("News Source Data")})}) { p-> Column(Modifier.fillMaxSize().padding(p).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text(if(signup) "புதிய கணக்கு உருவாக்கு" else "உள்நுழைக",style=MaterialTheme.typography.headlineSmall)
   OutlinedTextField(email,{email=it},Modifier.fillMaxWidth(),label={Text("மின்னஞ்சல்")},singleLine=true)
   OutlinedTextField(password,{password=it},Modifier.fillMaxWidth(),label={Text("கடவுச்சொல் (குறைந்தது 6 எழுத்துகள்)")},singleLine=true)
   Button(enabled=!busy&&email.trim().contains("@")&&password.length>=6,onClick={scope.launch {
    busy=true; message=""
    try {
     withContext(Dispatchers.IO){if(signup) api.signUp(email,password) else api.signIn(email,password)}
     signed=api.isSignedIn
     if(!signed) message="மின்னஞ்சல் உறுதிப்படுத்தல் தேவை. உங்கள் மின்னஞ்சலைச் சரிபார்க்கவும்."
    } catch(e:Exception) {
     message=when {
      e is java.net.SocketTimeoutException -> "சேவையகத்துடன் இணைக்க நேரம் முடிந்தது. இணைய இணைப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்."
      e is java.net.UnknownHostException -> "இணையம் அல்லது Supabase சேவையகத்தை அணுக முடியவில்லை. இணைய இணைப்பைச் சரிபார்க்கவும்."
      e.message?.contains("Invalid login credentials",true)==true -> "மின்னஞ்சல் அல்லது கடவுச்சொல் தவறாக உள்ளது."
      else -> e.message?.takeIf{it.isNotBlank()} ?: "உள்நுழைவு தோல்வியடைந்தது. மீண்டும் முயற்சிக்கவும்."
     }
    } finally { busy=false }
   }},modifier=Modifier.fillMaxWidth()){
    if(busy) { CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp); Spacer(Modifier.width(8.dp)) }
    Text(if(busy)"சரிபார்க்கிறது…" else if(signup)"கணக்கு உருவாக்கு" else "உள்நுழை")
   }
   TextButton(enabled=!busy,onClick={signup=!signup;message=""}){Text(if(signup)"உள்நுழைக" else "புதிய கணக்கு உருவாக்கவும்")}
   if(message.isNotBlank()) Text(message,color=MaterialTheme.colorScheme.error)
  } }
  return
 }
 Scaffold(
  topBar={TopAppBar(title={if(form) Text(listOf("புதிய செய்தி","புதிய அசைன்மென்ட்","புதிய தொடர்பு")[tab]) else Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) { Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.size(38.dp)) { Box(contentAlignment=androidx.compose.ui.Alignment.Center) { Text("N",style=TextStyle(brush=Brush.linearGradient(listOf(MaterialTheme.colorScheme.onPrimary,MaterialTheme.colorScheme.tertiary)),fontWeight=FontWeight.Black,fontSize=24.sp)) } }; Column(verticalArrangement=Arrangement.spacedBy(0.dp)) { Text("News Source Data", style=TextStyle(brush=Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)), fontWeight=FontWeight.ExtraBold, letterSpacing=0.3.sp)); Text("NEWS • ASSIGNMENTS • CONTACTS", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant) } }},navigationIcon={if(form) TextButton(onClick={form=false;formStep=0;editingId=null}){Text("ரத்து")} },actions={if(!form) { TextButton(onClick={load()}){Text("↻")}; TextButton(onClick={api.signOut();signed=false}){Text("⇥")} }})},
  bottomBar={if(!form) NavigationBar(containerColor=MaterialTheme.colorScheme.surface, tonalElevation=8.dp) { listOf("செய்திகள்","அசைன்மென்ட்","தொடர்புகள்").forEachIndexed{i,l->NavigationBarItem(selected=tab==i,onClick={tab=i;form=false;search="";selectedDate="";selectedCategory="அனைத்தும்"},icon={Text(listOf("▤","▦","♧")[i],style=MaterialTheme.typography.titleLarge,fontWeight=if(tab==i) FontWeight.Bold else FontWeight.Normal)},label={Text(l,style=MaterialTheme.typography.labelMedium)})} }},
  floatingActionButton={if(form) FloatingActionButton(onClick={if(!busy && (if(tab==0) body.isNotBlank() else if(tab==1) body.isNotBlank() && (formStep==0 || (eventTime.isNotBlank() && eventEndTime.isNotBlank())) else title.isNotBlank()&&body.isNotBlank())) { if(tab==0&&formStep==0) formStep=1 else if(tab==1&&formStep==0) formStep=1 else if(tab==0&&formStep==1) formStep=2 else saveEntry() }}){Text(if(busy)"…" else "✓",style=MaterialTheme.typography.titleLarge)} else FloatingActionButton(onClick={showAddMenu=true}){Text("+",style=MaterialTheme.typography.headlineMedium)}}
 ) { p ->
 if(signed && sharedDraft.isNotBlank() && !form) AlertDialog(onDismissRequest={sharedDraft=""},title={Text("பகிரப்பட்ட உரை கிடைத்துள்ளது")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("இந்த உரையை எதற்காகச் சேமிக்க வேண்டும்?");Text(sharedDraft.take(350),maxLines=6,overflow=TextOverflow.Ellipsis)}},confirmButton={Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick={val draft=sharedDraft;sharedDraft="";startEntry(0);body=draft}){Text("செய்தியாகச் சேர்")};TextButton(onClick={val draft=sharedDraft;sharedDraft="";startEntry(1);body=draft}){Text("அசைன்மென்ட்டாகச் சேர்")}}},dismissButton={TextButton(onClick={sharedDraft=""}){Text("பின்னர்")}})
 if(showAddMenu) AlertDialog(onDismissRequest={showAddMenu=false},title={Text("புதிய பதிவு")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){listOf("புதிய செய்தி","புதிய அசைன்மென்ட்","புதிய தொடர்பு").forEachIndexed{i,l->OutlinedButton(onClick={startEntry(i)},modifier=Modifier.fillMaxWidth()){Text(l)}}}},confirmButton={TextButton(onClick={showAddMenu=false}){Text("மூடு")}})
 Column((if(form) Modifier.fillMaxSize().padding(p).padding(horizontal=16.dp,vertical=8.dp).verticalScroll(rememberScrollState()) else Modifier.fillMaxSize().padding(p).padding(horizontal=16.dp,vertical=10.dp)),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  if(form) {
   if(tab==0 && formStep==0) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Text("புதிய செய்தி",style=MaterialTheme.typography.titleMedium); TextButton(onClick={cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))}) { Text("▣ OCR") } }
    OutlinedTextField(body,{body=it},Modifier.fillMaxWidth().heightIn(min=560.dp),label={Text("முதல் வரியே தலைப்பு; அடுத்த வரிகளில் செய்தி")},minLines=24,maxLines=300)
   } else if(tab==0) {
    Text(if(formStep==1)"எந்த மாவட்டம்?" else "செய்தித் துறை மற்றும் கேட்டகிரி",style=MaterialTheme.typography.titleLarge)
    if(formStep==1) {
     listOf("திருநெல்வேலி","தென்காசி").forEach{FilterChip(selected=district==it,onClick={district=it},label={Text(it)})}
    } else {
     Text("எந்தத் துறை?")
     departments.forEach{FilterChip(selected=department==it,onClick={department=it},label={Text(it)})}
     Text("கேட்டகிரி")
     (categories+userCategories).distinct().forEach{FilterChip(selected=category==it,onClick={category=it},label={Text(it)})}
     Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(customCategory,{customCategory=it},Modifier.weight(1f),label={Text("புதிய கேட்டகிரி")},singleLine=true); Button(onClick={val v=customCategory.trim(); if(v.isNotBlank()){userCategories=(userCategories+v).distinct();category=v;customCategory=""}} ,enabled=customCategory.isNotBlank()){Text("சேர்")} }
    }
   } else if(tab==1 && formStep==0) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Text("அசைன்மென்ட் விவரம்",style=MaterialTheme.typography.titleMedium); TextButton(onClick={cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))}) { Text("▣ OCR") } }
    OutlinedTextField(body,{body=it},Modifier.fillMaxWidth().heightIn(min=560.dp),label={Text("முதல் வரியே அசைன்மென்ட் தலைப்பு; அடுத்த வரிகளில் விவரம்")},minLines=24,maxLines=300)
   } else if(tab==1) {
    Text("நிகழ்ச்சி விவரங்கள்",style=MaterialTheme.typography.titleLarge)
    Text("சேமிக்கத் தொடக்க நேரமும் முடிவு நேரமும் தேர்வு செய்ய வேண்டும்.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick={val cal=java.util.Calendar.getInstance(); DatePickerDialog(context,{_,y,m,d->eventDate=String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d)},cal.get(java.util.Calendar.YEAR),cal.get(java.util.Calendar.MONTH),cal.get(java.util.Calendar.DAY_OF_MONTH)).show()},modifier=Modifier.fillMaxWidth()) { Text("தொடக்க தேதி: $eventDate  📅") }
    OutlinedButton(onClick={val cal=java.util.Calendar.getInstance(); TimePickerDialog(context,{_,h,m->eventTime=String.format(Locale.US,"%02d:%02d",h,m)},cal.get(java.util.Calendar.HOUR_OF_DAY),cal.get(java.util.Calendar.MINUTE),true).show()},modifier=Modifier.fillMaxWidth()) { Text("ஆரம்ப நேரம்: " + eventTime.ifBlank { "தேர்வு செய்யவும்" } + " 🕒") }
    OutlinedButton(onClick={val cal=java.util.Calendar.getInstance(); DatePickerDialog(context,{_,y,m,d->eventEndDate=String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d)},cal.get(java.util.Calendar.YEAR),cal.get(java.util.Calendar.MONTH),cal.get(java.util.Calendar.DAY_OF_MONTH)).show()},modifier=Modifier.fillMaxWidth()) { Text("முடிவு தேதி: $eventEndDate  📅") }
    OutlinedButton(onClick={val cal=java.util.Calendar.getInstance(); TimePickerDialog(context,{_,h,m->eventEndTime=String.format(Locale.US,"%02d:%02d",h,m)},cal.get(java.util.Calendar.HOUR_OF_DAY),cal.get(java.util.Calendar.MINUTE),true).show()},modifier=Modifier.fillMaxWidth()) { Text("முடிவு நேரம்: " + eventEndTime.ifBlank { "தேர்வு செய்யவும்" } + " 🕒") }
    OutlinedTextField(eventPlace,{eventPlace=it},Modifier.fillMaxWidth(),label={Text("இடம் (விருப்பம்)")},singleLine=true)
    Text("மாவட்டம்")
    listOf("திருநெல்வேலி","தென்காசி").forEach{FilterChip(selected=district==it,onClick={district=it},label={Text(it)})}
   } else {
    Text("புதிய தொடர்பு",style=MaterialTheme.typography.titleLarge)
    OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("முழுப் பெயர்")},singleLine=true)
    OutlinedTextField(body,{body=it},Modifier.fillMaxWidth(),label={Text("தொலைபேசி எண்")},singleLine=true)
    OutlinedTextField(contactExtra,{contactExtra=it},Modifier.fillMaxWidth(),label={Text("குறிப்புகள்")},minLines=3)
   }
   if(message.isNotBlank()) Text(message,color=MaterialTheme.colorScheme.error)
  } else {
   
   if(tab==1) PremiumMonthCalendar(year=calendarYear,month=calendarMonth,selectedDate=calendarDay,entries=rows,onSelect={calendarDay=it;selectedDate=it},onMonthChange={delta-> val cal=java.util.Calendar.getInstance(); cal.set(calendarYear,calendarMonth,1); cal.add(java.util.Calendar.MONTH,delta); calendarYear=cal.get(java.util.Calendar.YEAR); calendarMonth=cal.get(java.util.Calendar.MONTH) },onAdd={startEntry(1);eventDate=calendarDay;eventEndDate=calendarDay})
   if(tab!=1) Surface(color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.55f),shape=MaterialTheme.shapes.extraLarge,modifier=Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=13.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
     Text("⌕",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     Box(Modifier.weight(1f)) { if(search.isBlank()) Text("செய்தி, தலைப்பு அல்லது இடத்தைத் தேடுங்கள்",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium); BasicTextField(value=search,onValueChange={search=it},singleLine=true,textStyle=MaterialTheme.typography.bodyLarge.copy(color=MaterialTheme.colorScheme.onSurface),modifier=Modifier.fillMaxWidth()) }
     TextButton(onClick={val cal=java.util.Calendar.getInstance(); DatePickerDialog(context,{_,y,m,d->selectedDate=String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d)},cal.get(java.util.Calendar.YEAR),cal.get(java.util.Calendar.MONTH),cal.get(java.util.Calendar.DAY_OF_MONTH)).show()}) { Text(if(selectedDate.isBlank()) "▦" else selectedDate.substring(5)) }
    }
   }
   if(selectedDate.isNotBlank()) TextButton(onClick={selectedDate=""}) { Text("தேதி வடிகட்டியை நீக்கு  ×") }
   if(tab==0) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { (listOf("அனைத்தும்")+(categories+userCategories).distinct()).forEach { cat -> FilterChip(selected=selectedCategory==cat,onClick={selectedCategory=cat},label={Text(cat)},border=null) } }
   if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
   if(message.isNotBlank()) Text(message,color=MaterialTheme.colorScheme.primary)
   val visibleRows=rows.filter{(it.title.contains(search,true)||it.body.contains(search,true)||it.meta.contains(search,true)) && (selectedDate.isBlank() || it.date==selectedDate) && (tab!=0 || selectedCategory=="அனைத்தும்" || it.meta.contains(selectedCategory,true))}
   val pageSize=10
   val pageCount=((visibleRows.size+pageSize-1)/pageSize).coerceAtLeast(1)
   if(page>=pageCount) page=pageCount-1
   val pageRows=visibleRows.drop(page*pageSize).take(pageSize)
   if(visibleRows.isEmpty()&&!busy) Text("பதிவுகள் இல்லை. + பொத்தானைத் தொட்டு சேர்க்கவும்.")
   LazyColumn(verticalArrangement=Arrangement.spacedBy(0.dp),contentPadding=PaddingValues(bottom=8.dp)){items(pageRows,key={it.id}){e->Column(Modifier.fillMaxWidth().clickable { selectedEntry=e }.padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
    if(tab==1) Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
     Text("◷",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
     Text(e.date.ifBlank{"தேதி இல்லை"} + if(e.startTime.isNotBlank()) "  ${e.startTime}" else "",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
     if(e.endDate.isNotBlank()||e.endTime.isNotBlank()) Text("→ ${e.endDate.ifBlank{e.date}} ${e.endTime}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(e.title,style=MaterialTheme.typography.titleMedium)
    if(e.body.isNotBlank()) { val previewBody=if(tab==0||tab==1) e.body.lineSequence().drop(1).joinToString("\n").trim() else e.body; if(previewBody.isNotBlank()) Text(previewBody,maxLines=3,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    if(e.meta.isNotBlank()) Text(e.meta,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(tab==2) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     FilledTonalButton(onClick={val number=e.phone.filter{it.isDigit()||it=='+'};context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:$number")))},contentPadding=PaddingValues(horizontal=12.dp,vertical=4.dp)){Text("☎")}
     FilledTonalButton(onClick={val number=e.phone.filter{it.isDigit()};context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/$number")))},contentPadding=PaddingValues(horizontal=12.dp,vertical=4.dp)){Text("◉",style=MaterialTheme.typography.titleMedium)}
    } else Row(horizontalArrangement=Arrangement.End,modifier=Modifier.fillMaxWidth()){
     TextButton(onClick={val s=e.title+"\n\n"+e.body;context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,s),"பகிரவும்"))}){Text("பகிர்")}
     TextButton(onClick={title=e.title;body=e.body;category=e.meta.ifBlank{"மற்றவை"};editingId=e.id;if(tab==1)eventDate=e.date;form=true;formStep=0}){Text("திருத்து")}
     TextButton(onClick={deleteTarget=e}){Text("நீக்கு")}
    }
   }
  }}
  if(pageCount>1) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { (0 until pageCount).forEach { p -> TextButton(onClick={page=p},enabled=p!=page) { Text((p+1).toString(),fontWeight=if(p==page) FontWeight.Bold else FontWeight.Normal) } } }
  }
 }
 }
 if(selectedEntry!=null) {
  val e=selectedEntry!!
  AlertDialog(onDismissRequest={selectedEntry=null},title={Text(e.title)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){if(e.meta.isNotBlank())Text(e.meta); val detailBody=if(tab==0||tab==1) e.body.lineSequence().drop(1).joinToString("\n").trim() else e.body; Text(detailBody.ifBlank{"விவரங்கள் இல்லை"})}},confirmButton={TextButton(onClick={selectedEntry=null}){Text("மூடு")}},dismissButton={TextButton(onClick={
   title=e.title; body=e.body; category=e.meta.ifBlank{"மற்றவை"}; editingId=e.id
   if(tab==2) contactExtra=e.meta
   if(tab==1) eventDate=e.date
   selectedEntry=null; form=true; formStep=0
  }){Text("திருத்து")}})
 }
 if(deleteTarget!=null) AlertDialog(onDismissRequest={deleteTarget=null},title={Text("பதிவை நீக்கவா?")},text={Text("இந்தப் பதிவு நீக்கப்படும். இந்தச் செயலைத் திரும்பப் பெற முடியாது.")},confirmButton={TextButton(onClick={val e=deleteTarget!!;deleteTarget=null;scope.launch{busy=true;try{withContext(Dispatchers.IO){api.delete(listOf("news_items","assignments","contacts")[tab],e.id)};message="நீக்கப்பட்டது";load()}catch(ex:Exception){message=ex.message ?: "நீக்க முடியவில்லை"};busy=false}}){Text("நீக்கு")}},dismissButton={TextButton(onClick={deleteTarget=null}){Text("ரத்து")}})
}


@Composable
private fun PremiumMonthCalendar(year:Int, month:Int, selectedDate:String, entries:List<Entry>, onSelect:(String)->Unit, onMonthChange:(Int)->Unit, onAdd:()->Unit) {
 val cal=java.util.Calendar.getInstance()
 cal.set(year,month,1)
 val days=cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
 val offset=(cal.get(java.util.Calendar.DAY_OF_WEEK)+5)%7
 val monthName=SimpleDateFormat("MMMM yyyy", Locale("ta","IN")).format(cal.time)
 val eventDates=entries.mapNotNull { it.date.takeIf { d -> d.isNotBlank() } }.toSet()
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
   Column { Text("நிகழ்ச்சி காலண்டர்",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold); Text(monthName,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
   Row { IconButton(onClick={onMonthChange(-1)}){Text("‹",style=MaterialTheme.typography.headlineMedium)}; IconButton(onClick={onMonthChange(1)}){Text("›",style=MaterialTheme.typography.headlineMedium)} }
  }
  Row(Modifier.fillMaxWidth()) { listOf("தி","செ","பு","வி","வெ","ச","ஞா").forEach { Text(it,Modifier.weight(1f),textAlign=TextAlign.Center,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
  val cellCount=((offset+days+6)/7)*7
  for(week in 0 until cellCount/7) {
   Row(Modifier.fillMaxWidth()) {
    for(col in 0..6) {
     val day=week*7+col-offset+1
     Box(Modifier.weight(1f).height(30.dp),contentAlignment=androidx.compose.ui.Alignment.Center) {
      if(day in 1..days) {
       val date=String.format(Locale.US,"%04d-%02d-%02d",year,month+1,day)
       val chosen=date==selectedDate
       Surface(onClick={onSelect(date)},shape=MaterialTheme.shapes.large,color=if(chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,contentColor=if(chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxSize(),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
         Text(day.toString(),style=MaterialTheme.typography.bodyMedium,fontWeight=if(chosen) FontWeight.Bold else FontWeight.Normal)
         if(date in eventDates) Box(Modifier.size(4.dp)) { Surface(Modifier.fillMaxSize(),shape=MaterialTheme.shapes.small,color=if(chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary){} }
        }
       }
      }
     }
    }
   }
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Start,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
   Column { Text("தேர்ந்தெடுத்த தேதி",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant); Text(selectedDate,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold) }
  }
  val dayEntries=entries.filter{it.date==selectedDate}
  if(dayEntries.isNotEmpty()) {
   Text("இந்த நாளின் நிகழ்வுகள்",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
   dayEntries.forEach { e -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) { Text(e.startTime.ifBlank{"—"},style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary); Column(Modifier.weight(1f)) { Text(e.title,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold); Text("முடிவு: ${e.endDate.ifBlank{e.date}} ${e.endTime}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } } }
  } else Text("இந்த நாளில் நிகழ்வுகள் இல்லை",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }
}
