package `in`.newssourcedata.app

import android.content.Context
import android.content.SharedPreferences
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.Locale

data class AppProfile(
    val id: String,
    val displayName: String,
    val role: String,
    val approvalStatus: String
)

data class District(val id: String, val name: String)

data class AppDeviceApproval(val deviceId: String, val displayName: String, val status: String, val createdAt: String)

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
    val createdAt: String?,
    val attachmentPaths: List<String> = emptyList()
)

data class StockVideoRecord(
    val id: String,
    val youtubeVideoId: String,
    val youtubeUrl: String,
    val title: String,
    val thumbnailUrl: String,
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
    private val appContext: Context = context.applicationContext
    private val preferences: SharedPreferences =
        context.getSharedPreferences("news_source_supabase_session", Context.MODE_PRIVATE)

    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    fun hasSession(): Boolean =
        !preferences.getString("access_token", null).isNullOrBlank() &&
            !preferences.getString("refresh_token", null).isNullOrBlank()

    private val sharedLoginEmail = "kariyandimuthu@gmail.com"

    fun getDeviceDisplayName(): String =
        preferences.getString("device_display_name", null).orEmpty()

    fun getRegisteredDeviceStatus(): String =
        preferences.getString("device_status", null).orEmpty()

    fun setDeviceDisplayName(name: String) {
        preferences.edit().putString("device_display_name", name.trim()).apply()
    }

    private fun ensureDeviceIdentity(): Pair<String, String> {
        val deviceId = preferences.getString("device_id", null)
            ?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val deviceSecret = preferences.getString("device_secret", null)
            ?.takeIf { it.isNotBlank() } ?: (UUID.randomUUID().toString() + UUID.randomUUID().toString())
        preferences.edit()
            .putString("device_id", deviceId)
            .putString("device_secret", deviceSecret)
            .apply()
        return deviceId to deviceSecret
    }

    private fun clearStoredSession() {
        val preservedKeys = listOf("device_display_name", "device_id", "device_secret", "device_status")
        val preserved = preservedKeys.associateWith { preferences.getString(it, null) }
        val editor = preferences.edit().clear()
        preserved.forEach { (key, value) ->
            if (!value.isNullOrBlank()) editor.putString(key, value)
        }
        editor.apply()
    }

    suspend fun signIn(password: String) = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || publishableKey.isBlank()) {
            throw IllegalStateException("Supabase அமைப்புகள் இல்லை. Build configuration-ஐ சரிபார்க்கவும்.")
        }
        val result = JSONObject(
            request(
                path = "/auth/v1/token?grant_type=password",
                method = "POST",
                body = JSONObject().put("email", sharedLoginEmail).put("password", password),
                authenticated = false
            )
        )
        saveSession(result)
        if (!hasSession()) {
            throw IllegalStateException("உள்நுழைவு வெற்றியடைந்தாலும் session கிடைக்கவில்லை.")
        }
    }

    suspend fun registerDevice(displayName: String): String = withContext(Dispatchers.IO) {
        val cleanName = displayName.trim()
        if (cleanName.isBlank()) throw IllegalStateException("உங்கள் பெயரை உள்ளிடவும்.")
        if (!hasSession()) throw IllegalStateException("முதலில் உள்நுழையவும்.")
        setDeviceDisplayName(cleanName)
        val (deviceId, deviceSecret) = ensureDeviceIdentity()
        val response = JSONObject(
            request(
                "/rest/v1/rpc/register_app_device",
                "POST",
                JSONObject()
                    .put("p_device_id", deviceId)
                    .put("p_device_name", cleanName)
                    .put("p_device_secret", deviceSecret)
            )
        )
        val status = response.optString("status").lowercase(Locale.ROOT)
        if (status !in setOf("owner", "approved", "pending", "rejected", "revoked")) {
            throw IllegalStateException("சாதனப் பதிவு நிலையைச் சேவையகம் வழங்கவில்லை.")
        }
        val resolvedName = response.optString("display_name", cleanName).ifBlank { cleanName }
        preferences.edit()
            .putString("device_display_name", resolvedName)
            .putString("device_status", status)
            .apply()
        status
    }

    suspend fun getPendingDeviceApprovals(): List<AppDeviceApproval> = withContext(Dispatchers.IO) {
        val deviceId = preferences.getString("device_id", null)
            ?: throw IllegalStateException("நிர்வாகச் சாதன அடையாளம் கிடைக்கவில்லை.")
        val deviceSecret = preferences.getString("device_secret", null)
            ?: throw IllegalStateException("நிர்வாகச் சாதனத் திறவுகோல் கிடைக்கவில்லை.")
        val rows = JSONArray(
            request(
                "/rest/v1/rpc/list_pending_app_devices",
                "POST",
                JSONObject()
                    .put("p_owner_device_id", deviceId)
                    .put("p_owner_secret", deviceSecret)
            )
        )
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(
                    AppDeviceApproval(
                        deviceId = row.optString("device_id"),
                        displayName = row.optString("display_name", "பெயரில்லா சாதனம்"),
                        status = row.optString("status", "pending"),
                        createdAt = row.optString("created_at", "")
                    )
                )
            }
        }
    }

    suspend fun setDeviceApproval(deviceId: String, approved: Boolean) = withContext(Dispatchers.IO) {
        val ownerDeviceId = preferences.getString("device_id", null)
            ?: throw IllegalStateException("நிர்வாகச் சாதன அடையாளம் கிடைக்கவில்லை.")
        val ownerSecret = preferences.getString("device_secret", null)
            ?: throw IllegalStateException("நிர்வாகச் சாதனத் திறவுகோல் கிடைக்கவில்லை.")
        request(
            "/rest/v1/rpc/set_app_device_approval",
            "POST",
            JSONObject()
                .put("p_owner_device_id", ownerDeviceId)
                .put("p_owner_secret", ownerSecret)
                .put("p_target_device_id", deviceId)
                .put("p_approve", approved)
        )
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            if (hasSession()) {
                request("/auth/v1/logout?scope=local", "POST", null, authenticated = true)
            }
        } catch (_: Exception) {
            // Clear the local session even when the device is offline.
        } finally {
            clearStoredSession()
        }
    }

    suspend fun changeSharedPassword(currentPassword: String, newPassword: String) = withContext(Dispatchers.IO) {
        val email = preferences.getString("email", null)?.trim().orEmpty()
        if (email.isBlank()) throw IllegalStateException("உள்நுழைந்த மின்னஞ்சல் கிடைக்கவில்லை. மீண்டும் உள்நுழையவும்.")

        val verifyResponse = executeHttp(
            "/auth/v1/token?grant_type=password",
            "POST",
            JSONObject().put("email", email).put("password", currentPassword),
            null
        )
        if (verifyResponse.status !in 200..299) {
            throw IllegalStateException(friendlyError(verifyResponse.body, verifyResponse.status))
        }
        saveSession(JSONObject(verifyResponse.body))
        val freshToken = preferences.getString("access_token", null)
            ?: throw IllegalStateException("சரிபார்ப்பு முடிந்தது; session token கிடைக்கவில்லை.")

        val updateResponse = executeHttp(
            "/auth/v1/user",
            "PUT",
            JSONObject().put("password", newPassword),
            freshToken
        )
        if (updateResponse.status !in 200..299) {
            throw IllegalStateException(friendlyError(updateResponse.body, updateResponse.status))
        }

        val globalLogoutResponse = try {
            executeHttp("/auth/v1/logout?scope=global", "POST", null, freshToken)
        } catch (_: Exception) {
            null
        }
        val cutoffResponse = try {
            executeHttp("/rest/v1/rpc/revoke_all_shared_sessions", "POST", JSONObject(), freshToken)
        } catch (_: Exception) {
            null
        }

        clearStoredSession()

        if (globalLogoutResponse == null || globalLogoutResponse.status !in 200..299 ||
            cutoffResponse == null || cutoffResponse.status !in 200..299
        ) {
            throw IllegalStateException(
                "கடவுச்சொல் மாற்றப்பட்டது. ஆனால் பழைய session-களை முழுமையாக ரத்து செய்ததை உறுதிப்படுத்த முடியவில்லை. புதிய கடவுச்சொல்லுடன் மீண்டும் உள்நுழைந்து, வேறு சாதனங்களின் அணுகலையும் சரிபார்க்கவும்."
            )
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
                "/rest/v1/news_items?select=id,headline,body,summary,news_date,district_id,category,status,priority,location_text,created_at,attachment_paths&order=news_date.desc,created_at.desc",
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
                        createdAt = optionalString(row, "created_at"),
                        attachmentPaths = buildList {
                            val paths = row.optJSONArray("attachment_paths") ?: JSONArray()
                            for (pathIndex in 0 until paths.length()) {
                                val value = paths.optString(pathIndex).trim()
                                if (value.isNotBlank()) add(value)
                            }
                        }
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
        location: String,
        attachmentPaths: List<String> = emptyList()
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
            .put("attachment_paths", JSONArray(attachmentPaths))
        request("/rest/v1/news_items", "POST", payload)
    }

    suspend fun getStockVideos(): List<StockVideoRecord> = withContext(Dispatchers.IO) {
        val rows = JSONArray(
            request(
                "/rest/v1/stock_videos?select=id,youtube_video_id,youtube_url,title,thumbnail_url,created_at&order=created_at.desc",
                "GET",
                null
            )
        )
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(
                    StockVideoRecord(
                        id = row.optString("id"),
                        youtubeVideoId = row.optString("youtube_video_id"),
                        youtubeUrl = row.optString("youtube_url"),
                        title = row.optString("title"),
                        thumbnailUrl = row.optString("thumbnail_url"),
                        createdAt = optionalString(row, "created_at")
                    )
                )
            }
        }
    }

    suspend fun saveStockVideo(rawUrl: String) = withContext(Dispatchers.IO) {
        val videoId = extractYouTubeVideoId(rawUrl)
            ?: throw IllegalStateException("சரியான YouTube வீடியோ இணைப்பை உள்ளிடவும்.")
        val canonicalUrl = "https://www.youtube.com/watch?v=$videoId"
        val connection = (URL("https://www.youtube.com/oembed?url=" + encode(canonicalUrl) + "&format=json")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
        }
        val metadata = try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("இந்த YouTube வீடியோ விவரங்களைப் பெற முடியவில்லை.")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
        val title = optionalString(metadata, "title")?.trim().orEmpty()
        if (title.isBlank()) throw IllegalStateException("YouTube தலைப்பைப் பெற முடியவில்லை.")
        request(
            "/rest/v1/stock_videos",
            "POST",
            JSONObject()
                .put("owner_id", requireUserId())
                .put("youtube_video_id", videoId)
                .put("youtube_url", canonicalUrl)
                .put("title", title)
                .put("thumbnail_url", "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
        )
    }

    suspend fun deleteStockVideo(id: String) = withContext(Dispatchers.IO) {
        request("/rest/v1/stock_videos?id=eq." + encode(id), "DELETE", null)
    }

    suspend fun uploadNewsPhoto(uri: Uri): String = withContext(Dispatchers.IO) {
        val userId = requireUserId()
        val bytes = compressPhoto(uri)
        if (bytes.isEmpty()) throw IllegalStateException("படத்தைச் சுருக்க முடியவில்லை.")
        val path = "$userId/${UUID.randomUUID()}.jpg"
        storageRequest("/storage/v1/object/news-photos/" + encodeStoragePath(path), "POST", bytes, "image/jpeg")
        path
    }

    suspend fun getNewsPhotoBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        storageRequest("/storage/v1/object/authenticated/news-photos/" + encodeStoragePath(path), "GET", null, null)
    }

    suspend fun deleteNewsPhoto(path: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("prefixes", JSONArray().put(path))
        val response = executeHttp("/storage/v1/object/news-photos", "DELETE", body, currentAccessToken())
        if (response.status !in 200..299) {
            throw IllegalStateException(friendlyError(response.body, response.status))
        }
    }

    suspend fun downloadNewsPhoto(context: Context, path: String): String = withContext(Dispatchers.IO) {
        val bytes = getNewsPhotoBytes(path)
        if (bytes.isEmpty()) throw IllegalStateException("புகைப்படம் காலியாக உள்ளது.")
        val filename = "NewsSource-${System.currentTimeMillis()}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NewsSourceData")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val destination = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Download கோப்பை உருவாக்க முடியவில்லை.")
            try {
                context.contentResolver.openOutputStream(destination)?.use { it.write(bytes) }
                    ?: throw IllegalStateException("Download கோப்பில் எழுத முடியவில்லை.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(destination, values, null, null)
            } catch (error: Exception) {
                context.contentResolver.delete(destination, null, null)
                throw error
            }
        } else {
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("Download கோப்புறையை உருவாக்க முடியவில்லை.")
            File(directory, filename).writeBytes(bytes)
        }
        filename
    }

    private fun compressPhoto(uri: Uri): ByteArray {
        val raw = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("படத்தைத் திறக்க முடியவில்லை.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalStateException("தேர்ந்தெடுத்த கோப்பு செல்லுபடியான படம் அல்ல.")
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 1600) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw IllegalStateException("படத்தை வாசிக்க முடியவில்லை.")
        return try {
            ByteArrayOutputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 72, output)) {
                    throw IllegalStateException("படத்தை JPEG வடிவில் சுருக்க முடியவில்லை.")
                }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun currentAccessToken(): String =
        preferences.getString("access_token", null) ?: throw IllegalStateException("உள்நுழையவும்.")

    private fun storageRequest(
        path: String,
        method: String,
        body: ByteArray?,
        contentType: String?,
        allowRefresh: Boolean = true
    ): ByteArray {
        val token = currentAccessToken()
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("x-app-device-id", preferences.getString("device_id", "").orEmpty())
            setRequestProperty("x-app-device-secret", preferences.getString("device_secret", "").orEmpty())
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            if (method == "POST") setRequestProperty("x-upsert", "false")
            if (body != null) doOutput = true
        }
        return try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (code == 401 && allowRefresh && refreshSession()) {
                return storageRequest(path, method, body, contentType, allowRefresh = false)
            }
            if (code !in 200..299) {
                throw IllegalStateException(friendlyError(String(responseBytes, Charsets.UTF_8), code))
            }
            responseBytes
        } finally {
            connection.disconnect()
        }
    }

    private fun encodeStoragePath(path: String): String =
        path.split("/").joinToString("/") { encode(it).replace("+", "%20") }

    private fun extractYouTubeVideoId(raw: String): String? {
        val uri = runCatching { java.net.URI(raw.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
        if (host !in setOf("youtube.com", "m.youtube.com", "youtu.be", "youtube-nocookie.com")) return null
        val candidate = when {
            host == "youtu.be" -> uri.path.trim('/').substringBefore("/")
            uri.path == "/watch" -> uri.rawQuery.orEmpty().split("&")
                .firstOrNull { it.startsWith("v=") }?.substringAfter("v=")
            uri.path.startsWith("/shorts/") -> uri.path.removePrefix("/shorts/").substringBefore("/")
            uri.path.startsWith("/embed/") -> uri.path.removePrefix("/embed/").substringBefore("/")
            else -> null
        }
        return candidate?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
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
        districtId: String?, category: String, priority: String, location: String,
        attachmentPaths: List<String> = emptyList()
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
            .put("attachment_paths", JSONArray(attachmentPaths))
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
            clearStoredSession()
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
        preferences.getString("device_id", null)?.takeIf { it.isNotBlank() }?.let {
            connection.setRequestProperty("x-app-device-id", it)
        }
        preferences.getString("device_secret", null)?.takeIf { it.isNotBlank() }?.let {
            connection.setRequestProperty("x-app-device-secret", it)
        }
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