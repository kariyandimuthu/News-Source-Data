package `in`.newssourcedata.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AppProfile(
    val id: String,
    val displayName: String,
    val role: String,
    val approvalStatus: String
)

data class District(val id: String, val name: String)

data class NewsRecord(
    val id: String,
    val headline: String,
    val body: String,
    val summary: String,
    val newsDate: String,
    val districtId: String?,
    val category: String,
    val status: String,
    val priority: String,
    val location: String?,
    val createdAt: String?
)

data class AssignmentRecord(
    val id: String,
    val title: String,
    val description: String,
    val eventDate: String,
    val districtId: String?,
    val category: String,
    val status: String,
    val priority: String,
    val location: String?,
    val createdAt: String?
)

data class ContactRecord(
    val id: String,
    val fullName: String,
    val designation: String,
    val organization: String,
    val phone: String,
    val email: String,
    val districtId: String?,
    val notes: String,
    val createdAt: String?
)

class SupabaseApi(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("news_source_supabase_session", Context.MODE_PRIVATE)

    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    fun hasSession(): Boolean =
        !preferences.getString("access_token", null).isNullOrBlank() &&
            !preferences.getString("refresh_token", null).isNullOrBlank()

    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || publishableKey.isBlank()) {
            throw IllegalStateException("Supabase அமைப்புகள் இல்லை. Build configuration-ஐ சரிபார்க்கவும்.")
        }
        val result = JSONObject(
            request(
                path = "/auth/v1/token?grant_type=password",
                method = "POST",
                body = JSONObject().put("email", email.trim()).put("password", password),
                authenticated = false
            )
        )
        saveSession(result)
        if (!hasSession()) {
            throw IllegalStateException("உள்நுழைவு வெற்றியடைந்தாலும் session கிடைக்கவில்லை.")
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            if (hasSession()) {
                request("/auth/v1/logout", "POST", JSONObject(), authenticated = true)
            }
        } catch (_: Exception) {
            // Clear the local session even when the device is offline.
        } finally {
            preferences.edit().clear().apply()
        }
    }

    suspend fun getProfile(): AppProfile? = withContext(Dispatchers.IO) {
        val id = preferences.getString("user_id", null) ?: return@withContext null
        val rows = JSONArray(
            request(
                "/rest/v1/profiles?id=eq." + encode(id) +
                    "&select=id,display_name,role,approval_status",
                "GET",
                null
            )
        )
        if (rows.length() == 0) return@withContext null
        val row = rows.getJSONObject(0)
        AppProfile(
            id = row.optString("id"),
            displayName = row.optString("display_name", ""),
            role = row.optString("role", "reporter"),
            approvalStatus = row.optString("approval_status", "pending")
        )
    }

    suspend fun getDistricts(): List<District> = withContext(Dispatchers.IO) {
        val rows = JSONArray(request("/rest/v1/districts?select=id,name&order=name.asc", "GET", null))
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(District(row.optString("id"), row.optString("name")))
            }
        }
    }

    suspend fun getNews(): List<NewsRecord> = withContext(Dispatchers.IO) {
        val rows = JSONArray(
            request(
                "/rest/v1/news_items?select=id,headline,body,summary,news_date,district_id,category,status,priority,location_text,created_at&order=news_date.desc,created_at.desc",
                "GET",
                null
            )
        )
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(
                    NewsRecord(
                        id = row.optString("id"),
                        headline = row.optString("headline"),
                        body = row.optString("body", ""),
                        summary = row.optString("summary", ""),
                        newsDate = row.optString("news_date", ""),
                        districtId = optionalString(row, "district_id"),
                        category = row.optString("category", "general"),
                        status = row.optString("status", "draft"),
                        priority = row.optString("priority", "normal"),
                        location = optionalString(row, "location_text"),
                        createdAt = optionalString(row, "created_at")
                    )
                )
            }
        }
    }

    suspend fun getAssignments(): List<AssignmentRecord> = withContext(Dispatchers.IO) {
        val rows = JSONArray(
            request(
                "/rest/v1/assignments?select=id,title,description,event_date,district_id,category,status,priority,location_text,created_at&order=event_date.asc,created_at.desc",
                "GET",
                null
            )
        )
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(
                    AssignmentRecord(
                        id = row.optString("id"),
                        title = row.optString("title"),
                        description = row.optString("description", ""),
                        eventDate = row.optString("event_date", ""),
                        districtId = optionalString(row, "district_id"),
                        category = row.optString("category", "general"),
                        status = row.optString("status", "open"),
                        priority = row.optString("priority", "normal"),
                        location = optionalString(row, "location_text"),
                        createdAt = optionalString(row, "created_at")
                    )
                )
            }
        }
    }

    suspend fun getContacts(): List<ContactRecord> = withContext(Dispatchers.IO) {
        val rows = JSONArray(
            request(
                "/rest/v1/contacts?select=id,full_name,designation,organization,phone,email,district_id,notes,created_at&order=full_name.asc",
                "GET",
                null
            )
        )
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(
                    ContactRecord(
                        id = row.optString("id"),
                        fullName = row.optString("full_name"),
                        designation = row.optString("designation", ""),
                        organization = row.optString("organization", ""),
                        phone = row.optString("phone", ""),
                        email = row.optString("email", ""),
                        districtId = optionalString(row, "district_id"),
                        notes = row.optString("notes", ""),
                        createdAt = optionalString(row, "created_at")
                    )
                )
            }
        }
    }

    suspend fun addNews(
        headline: String,
        body: String,
        summary: String,
        newsDate: String,
        districtId: String?,
        category: String,
        priority: String,
        location: String
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("owner_id", requireUserId())
            .put("headline", headline.trim())
            .put("body", nullableValue(body))
            .put("summary", nullableValue(summary))
            .put("news_date", newsDate)
            .put("district_id", nullableValue(districtId))
            .put("category", category)
            .put("status", "draft")
            .put("priority", priority)
            .put("location_text", nullableValue(location))
        request("/rest/v1/news_items", "POST", payload)
    }

    suspend fun addAssignment(
        title: String,
        description: String,
        eventDate: String,
        districtId: String?,
        category: String,
        priority: String,
        location: String
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("owner_id", requireUserId())
            .put("title", title.trim())
            .put("description", nullableValue(description))
            .put("event_date", nullableValue(eventDate))
            .put("district_id", nullableValue(districtId))
            .put("category", category)
            .put("priority", priority)
            .put("status", "open")
            .put("location_text", nullableValue(location))
        request("/rest/v1/assignments", "POST", payload)
    }

    suspend fun addContact(
        fullName: String,
        designation: String,
        organization: String,
        phone: String,
        email: String,
        districtId: String?,
        notes: String
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("owner_id", requireUserId())
            .put("full_name", fullName.trim())
            .put("designation", nullableValue(designation))
            .put("organization", nullableValue(organization))
            .put("phone", nullableValue(phone))
            .put("email", nullableValue(email))
            .put("district_id", nullableValue(districtId))
            .put("notes", nullableValue(notes))
        request("/rest/v1/contacts", "POST", payload)
    }


    suspend fun updateNews(
        id: String, headline: String, body: String, summary: String, newsDate: String,
        districtId: String?, category: String, priority: String, location: String
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("headline", headline.trim())
            .put("body", nullableValue(body))
            .put("summary", nullableValue(summary))
            .put("news_date", newsDate)
            .put("district_id", nullableValue(districtId))
            .put("category", category)
            .put("priority", priority)
            .put("location_text", nullableValue(location))
        request("/rest/v1/news_items?id=eq." + encode(id), "PATCH", payload)
    }

    suspend fun updateAssignment(
        id: String, title: String, description: String, eventDate: String,
        districtId: String?, category: String, priority: String, location: String
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("title", title.trim())
            .put("description", nullableValue(description))
            .put("event_date", nullableValue(eventDate))
            .put("district_id", nullableValue(districtId))
            .put("category", category)
            .put("priority", priority)
            .put("location_text", nullableValue(location))
        request("/rest/v1/assignments?id=eq." + encode(id), "PATCH", payload)
    }

    suspend fun updateContact(
        id: String, fullName: String, designation: String, organization: String,
        phone: String, email: String, districtId: String?, notes: String
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("full_name", fullName.trim())
            .put("designation", nullableValue(designation))
            .put("organization", nullableValue(organization))
            .put("phone", nullableValue(phone))
            .put("email", nullableValue(email))
            .put("district_id", nullableValue(districtId))
            .put("notes", nullableValue(notes))
        request("/rest/v1/contacts?id=eq." + encode(id), "PATCH", payload)
    }

    suspend fun deleteNews(id: String) = withContext(Dispatchers.IO) {
        request("/rest/v1/news_items?id=eq." + encode(id), "DELETE", null)
    }

    suspend fun deleteAssignment(id: String) = withContext(Dispatchers.IO) {
        request("/rest/v1/assignments?id=eq." + encode(id), "DELETE", null)
    }

    suspend fun deleteContact(id: String) = withContext(Dispatchers.IO) {
        request("/rest/v1/contacts?id=eq." + encode(id), "DELETE", null)
    }

    private fun requireUserId(): String =
        preferences.getString("user_id", null)
            ?: throw IllegalStateException("மீண்டும் உள்நுழையவும்.")

    private fun saveSession(json: JSONObject) {
        val editor = preferences.edit()
        optionalString(json, "access_token")?.let { editor.putString("access_token", it) }
        optionalString(json, "refresh_token")?.let { editor.putString("refresh_token", it) }
        val user = json.optJSONObject("user")
        if (user != null) {
            optionalString(user, "id")?.let { editor.putString("user_id", it) }
            optionalString(user, "email")?.let { editor.putString("email", it) }
        }
        editor.apply()
    }

    private fun refreshSession(): Boolean {
        val refreshToken = preferences.getString("refresh_token", null) ?: return false
        return try {
            val response = executeHttp(
                "/auth/v1/token?grant_type=refresh_token",
                "POST",
                JSONObject().put("refresh_token", refreshToken),
                null
            )
            if (response.status !in 200..299) {
                false
            } else {
                saveSession(JSONObject(response.body))
                hasSession()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun request(
        path: String,
        method: String,
        body: JSONObject?,
        authenticated: Boolean = true,
        allowRefresh: Boolean = true
    ): String {
        val token = if (authenticated) {
            preferences.getString("access_token", null)
                ?: throw IllegalStateException("உள்நுழையவும்.")
        } else {
            null
        }
        val response = executeHttp(path, method, body, token)
        if (response.status == 401 && authenticated && allowRefresh) {
            if (refreshSession()) return request(path, method, body, authenticated, false)
            preferences.edit().clear().apply()
        }
        if (response.status !in 200..299) {
            throw IllegalStateException(friendlyError(response.body, response.status))
        }
        return response.body
    }

    private fun executeHttp(
        path: String,
        method: String,
        body: JSONObject?,
        bearerToken: String?
    ): HttpResponse {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("apikey", publishableKey)
        connection.setRequestProperty("Accept", "application/json")
        if (!bearerToken.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken)
        }
        if (body != null) {
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.doOutput = true
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            HttpResponse(status, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun friendlyError(body: String, status: Int): String {
        val message = try {
            val json = JSONObject(body)
            optionalString(json, "msg")
                ?: optionalString(json, "message")
                ?: optionalString(json, "error_description")
                ?: optionalString(json, "error")
        } catch (_: Exception) {
            null
        }
        return message?.take(240)
            ?: "சேவையகப் பிழை ($status). இணைய இணைப்பையும் Supabase அமைப்பையும் சரிபார்க்கவும்."
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun nullableValue(value: String?): Any =
        if (value.isNullOrBlank()) JSONObject.NULL else value.trim()

    private fun optionalString(json: JSONObject, key: String): String? {
        val value = json.opt(key)
        return if (value == null || value == JSONObject.NULL) null else value.toString()
    }

    private data class HttpResponse(val status: Int, val body: String)
}
