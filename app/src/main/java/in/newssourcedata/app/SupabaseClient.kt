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

    private fun execute(path: String, method: String, body: JSONObject?, auth: Boolean, prefer: String?, token: String?): Pair<Int, String> {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("apikey", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer " + if (auth) token.orEmpty() else apiKey)
            if (prefer != null) connection.setRequestProperty("Prefer", prefer)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to response
        } finally {
            connection.disconnect()
        }
    }

    @Synchronized
    private fun refreshSession(): Boolean {
        val refreshToken = prefs.getString("refresh_token", null)?.takeIf { it.isNotBlank() } ?: return false
        return try {
            val (code, response) = execute(
                "/auth/v1/token?grant_type=refresh_token", "POST",
                JSONObject().put("refresh_token", refreshToken), auth = false, prefer = null, token = null
            )
            if (code !in 200..299) {
                if (code == 400 || code == 401) signOut()
                false
            } else {
                val json = JSONObject(response)
                val access = json.optString("access_token")
                val refreshed = json.optString("refresh_token", refreshToken)
                if (access.isBlank()) false else {
                    prefs.edit().putString("access_token", access).putString("refresh_token", refreshed).apply()
                    true
                }
            }
        } catch (_: Exception) { false }
    }

    private fun request(path: String, method: String, body: JSONObject? = null, auth: Boolean = true, prefer: String? = null): String {
        var (code, response) = execute(path, method, body, auth, prefer, accessToken)
        if (auth && code == 401 && refreshSession()) {
            val retry = execute(path, method, body, auth = true, prefer = prefer, token = accessToken)
            code = retry.first
            response = retry.second
        }
        if (code !in 200..299) {
            val msg = try {
                val json = JSONObject(response)
                json.optString("msg").ifBlank { json.optString("message").ifBlank { json.optString("error_description").ifBlank { response } } }
            } catch (_: Exception) { response }
            if (code == 401 && auth) throw IllegalStateException("உங்கள் உள்நுழைவு காலாவதியாகிவிட்டது. மீண்டும் உள்நுழையவும். (HTTP 401)")
            throw IllegalStateException("HTTP $code: $msg")
        }
        return response
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

    fun update(table: String, id: String, values: JSONObject) {
        request("/rest/v1/$table?id=eq.$id", "PATCH", values, prefer = "return=minimal")
    }

    fun delete(table: String, id: String) {
        request("/rest/v1/$table?id=eq.$id", "DELETE", prefer = "return=minimal")
    }
}
