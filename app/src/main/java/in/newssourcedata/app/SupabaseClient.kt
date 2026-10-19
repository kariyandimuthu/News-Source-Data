package `in`.newssourcedata.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SupabaseClient(context: Context) {
    private val prefs = context.getSharedPreferences("news_source_auth", Context.MODE_PRIVATE)
    private val baseUrl = "https://cmvvcwcavopmpwkowhnz.supabase.co"
    private val apiKey = "sb_publishable_iY6fpaaFMuRpBpShDVSCtA_OeRglNDw"

    val accessToken: String? get() = prefs.getString("access_token", null)
    val isSignedIn: Boolean get() = !accessToken.isNullOrBlank()

    private fun request(path: String, method: String, body: JSONObject? = null, auth: Boolean = true, prefer: String? = null): String {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("apikey", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            if (auth && !accessToken.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $accessToken")
            else connection.setRequestProperty("Authorization", "Bearer $apiKey")
            if (prefer != null) connection.setRequestProperty("Prefer", prefer)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try { JSONObject(response).optString("msg", JSONObject(response).optString("message", response)) } catch (_: Exception) { response }
                throw IllegalStateException("HTTP $code: $msg")
            }
            return response
        } finally { connection.disconnect() }
    }

    fun signIn(email: String, password: String) {
        val response = request("/auth/v1/token?grant_type=password", "POST",
            JSONObject().put("email", email.trim()).put("password", password), auth = false)
        val json = JSONObject(response)
        val token = json.optString("access_token")
        if (token.isBlank()) throw IllegalStateException("உள்நுழைவு token கிடைக்கவில்லை")
        prefs.edit().putString("access_token", token).putString("refresh_token", json.optString("refresh_token")).putString("email", email.trim()).apply()
    }

    fun signUp(email: String, password: String) {
        val response = request("/auth/v1/signup", "POST",
            JSONObject().put("email", email.trim()).put("password", password), auth = false)
        val json = JSONObject(response)
        val token = json.optString("access_token")
        if (token.isNotBlank()) prefs.edit().putString("access_token", token).putString("refresh_token", json.optString("refresh_token")).putString("email", email.trim()).apply()
        else throw IllegalStateException("கணக்கு உருவாக்கப்பட்டது. மின்னஞ்சல் உறுதிப்படுத்தல் தேவைப்பட்டால் உறுதிப்படுத்திய பின் உள்நுழையவும்.")
    }

    fun signOut() { prefs.edit().remove("access_token").remove("refresh_token").remove("email").apply() }

    fun list(table: String): JSONArray = JSONArray(request("/rest/v1/$table?select=*&order=created_at.desc", "GET"))

    fun insert(table: String, values: JSONObject) {
        request("/rest/v1/$table", "POST", values, prefer = "return=minimal")
    }
}
