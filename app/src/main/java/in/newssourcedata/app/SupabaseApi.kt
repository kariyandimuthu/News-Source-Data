package `in`.newssourcedata.app

import android.content.Context
import android.content.SharedPreferences
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.net.URLEncoder
import java.io.InputStream
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
    val attachmentPaths: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)

data class StockVideoRecord(
    val id: String,
    val driveFileId: String,
    val driveUrl: String,
    val title: String,
    val previewUrl: String,
    val thumbnailUrl: String,
    val fileName: String?,
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

data class InfoEntryRecord(
    val id: String,
    val label: String,
    val value: String,
    val groupName: String,
    val sortOrder: Int,
    val updatedAt: String?
)

internal fun extractGoogleDriveFileId(raw: String): String? {
    val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
    val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
    if (host !in setOf("drive.google.com", "docs.google.com")) return null
    val path = uri.path.orEmpty()
    val query = uri.rawQuery.orEmpty().split("&").mapNotNull {
        val separator = it.indexOf('=')
        if (separator <= 0) null else {
            val key = it.substring(0, separator)
            val value = runCatching { java.net.URLDecoder.decode(it.substring(separator + 1), "UTF-8") }
                .getOrDefault(it.substring(separator + 1))
            key to value
        }
    }.toMap()
    val candidate = when {
        Regex("""/file/d/([^/]+)""").containsMatchIn(path) ->
            Regex("""/file/d/([^/]+)""").find(path)?.groupValues?.get(1)
        path == "/open" || path == "/uc" || path == "/file" -> query["id"]
        else -> null
    }
    return candidate?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,200}")) }
}


class SupabaseApi(context: Context) {
    private val appContext: Context = context.applicationContext
    private val preferences: SharedPreferences =
        context.getSharedPreferences("news_source_supabase_session", Context.MODE_PRIVATE)

    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val sessionRefreshLock = Any()

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
                "/rest/v1/news_items?select=id,headline,body,summary,news_date,district_id,category,status,priority,location_text,created_at,attachment_paths,tags&order=news_date.desc,created_at.desc",
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
                        },
                        tags = buildList {
                            val tags = row.optJSONArray("tags") ?: JSONArray()
                            for (tagIndex in 0 until tags.length()) {
                                val value = tags.optString(tagIndex).trim()
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

    suspend fun getInfoEntries(): List<InfoEntryRecord> = withContext(Dispatchers.IO) {
        val rows = JSONArray(
            request(
                "/rest/v1/info_entries?select=id,label,value,group_name,sort_order,updated_at&order=sort_order.asc,label.asc",
                "GET",
                null
            )
        )
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(
                    InfoEntryRecord(
                        id = row.optString("id"),
                        label = row.optString("label"),
                        value = row.optString("value", ""),
                        groupName = row.optString("group_name", ""),
                        sortOrder = row.optInt("sort_order", 0),
                        updatedAt = optionalString(row, "updated_at")
                    )
                )
            }
        }
    }

    suspend fun saveInfoEntry(id: String?, label: String, value: String, groupName: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("label", label.trim())
            .put("value", value.trim())
            .put("group_name", groupName.trim())
            .put("updated_at", java.time.Instant.now().toString())
        if (id.isNullOrBlank()) {
            payload.put("owner_id", requireUserId())
            request("/rest/v1/info_entries", "POST", payload)
        } else {
            request("/rest/v1/info_entries?id=eq." + encode(id), "PATCH", payload)
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
        attachmentPaths: List<String> = emptyList(),
        tags: List<String> = emptyList()
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
            .put("tags", JSONArray(tags))
        request("/rest/v1/news_items", "POST", payload)
    }

    suspend fun getStockVideos(): List<StockVideoRecord> = withContext(Dispatchers.IO) {
        val rows = JSONArray(
            request(
                "/rest/v1/stock_videos?select=id,drive_file_id,drive_url,title,preview_url,thumbnail_url,file_name,created_at&drive_file_id=not.is.null&order=created_at.desc",
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
                        driveFileId = row.optString("drive_file_id"),
                        driveUrl = row.optString("drive_url"),
                        title = row.optString("title"),
                        previewUrl = row.optString("preview_url"),
                        thumbnailUrl = row.optString("thumbnail_url"),
                        fileName = optionalString(row, "file_name"),
                        createdAt = optionalString(row, "created_at")
                    )
                )
            }
        }
    }

    suspend fun saveStockVideo(rawUrl: String) = withContext(Dispatchers.IO) {
        val fileId = extractGoogleDriveFileId(rawUrl)
            ?: throw IllegalStateException("சரியான Google Drive கோப்பு இணைப்பை உள்ளிடவும்.")
        val metadata = fetchGoogleDriveMetadata(fileId, rawUrl)
        val title = metadata.first.ifBlank { "Google Drive வீடியோ ${fileId.take(8)}" }
        val previewResourceKey = driveResourceKeyQuery(rawUrl).replaceFirst("&", "?")
        val previewUrl = "https://drive.google.com/file/d/$fileId/preview" + previewResourceKey
        val thumbnailUrl = "https://drive.google.com/thumbnail?id=$fileId&sz=w640" + driveResourceKeyQuery(rawUrl)
        request(
            "/rest/v1/stock_videos",
            "POST",
            JSONObject()
                .put("owner_id", requireUserId())
                .put("drive_file_id", fileId)
                .put("drive_url", rawUrl.trim())
                .put("title", title)
                .put("file_name", nullableValue(metadata.second))
                .put("preview_url", previewUrl)
                .put("thumbnail_url", thumbnailUrl)
                .put("youtube_video_id", JSONObject.NULL)
                .put("youtube_url", JSONObject.NULL)
        )
    }

    suspend fun downloadGoogleDriveVideo(context: Context, video: StockVideoRecord): String = withContext(Dispatchers.IO) {
        val fileId = extractGoogleDriveFileId(video.driveUrl)
            ?: video.driveFileId.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,200}")) }
            ?: throw IllegalStateException("Google Drive கோப்பு ID செல்லுபடியானதல்ல.")
        val connection = openGoogleDriveDownload(fileId, video.driveUrl)
        val mimeType = connection.contentType?.substringBefore(';')?.takeIf { it.isNotBlank() } ?: "video/mp4"
        val headerName = parseContentDispositionFileName(connection.getHeaderField("Content-Disposition"))
        val selectedName = safeDownloadFileName(headerName ?: video.fileName ?: video.title, mimeType, fileId)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, selectedName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NewsSourceData")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val destination = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (destination == null) {
            connection.disconnect()
            throw IllegalStateException("Downloads கோப்பை உருவாக்க முடியவில்லை.")
        }
        try {
            val input = connection.inputStream
            val output = context.contentResolver.openOutputStream(destination)
                ?: throw IllegalStateException("Downloads கோப்பை எழுத முடியவில்லை.")
            input.use { source ->
                output.use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count > 0) sink.write(buffer, 0, count)
                    }
                    sink.flush()
                }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(destination, values, null, null)
            selectedName
        } catch (error: Exception) {
            context.contentResolver.delete(destination, null, null)
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchGoogleDriveMetadata(fileId: String, rawUrl: String): Pair<String, String?> {
        val requestUrl = if (rawUrl.contains("resourcekey=")) rawUrl.trim()
            else "https://drive.google.com/file/d/$fileId/view?usp=sharing"
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Google Drive இணைப்பை அணுக முடியவில்லை (HTTP $code).")
            val html = connection.inputStream.use { readTextLimited(it, 2 * 1024 * 1024) }
            val title = findHtmlMeta(html, "og:title") ?: findHtmlTitle(html)?.removeSuffix(" - Google Drive")?.trim()
            val fileName = findHtmlMeta(html, "og:filename") ?: findHtmlMeta(html, "filename") ?: title
            cleanDriveTitle(title).ifBlank { "Google Drive வீடியோ ${fileId.take(8)}" } to
                fileName?.let(::cleanDriveTitle)?.takeIf { it.isNotBlank() }
        } catch (error: Exception) {
            if (error is IllegalStateException && error.message?.contains("Google Drive இணைப்பை") == true) throw error
            "Google Drive வீடியோ ${fileId.take(8)}" to null
        } finally {
            connection.disconnect()
        }
    }

    private fun openGoogleDriveDownload(fileId: String, originalUrl: String): HttpURLConnection {
        var nextUrl = URL("https://drive.google.com/uc?export=download&id=" +
            URLEncoder.encode(fileId, "UTF-8") + driveResourceKeyQuery(originalUrl))
        val cookies = linkedMapOf<String, String>()
        var confirmationTried = false
        repeat(8) {
            if (!isAllowedDriveHost(nextUrl.host)) {
                throw IllegalStateException("Google Drive-இன் பாதுகாப்பற்ற redirect தடுக்கப்பட்டது.")
            }
            val connection = (nextUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 180_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Encoding", "identity")
                if (cookies.isNotEmpty()) {
                    setRequestProperty("Cookie", cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" })
                }
            }
            val code = try {
                connection.responseCode
            } catch (_: Exception) {
                connection.disconnect()
                throw IllegalStateException("Google Drive-இலிருந்து இணைக்க முடியவில்லை. இணைய இணைப்பைச் சரிபார்க்கவும்.")
            }
            connection.headerFields.entries.filter { it.key.equals("Set-Cookie", true) }
                .flatMap { it.value.orEmpty() }
                .forEach { value ->
                    val pair = value.substringBefore(';')
                    val split = pair.indexOf('=')
                    if (split > 0) cookies[pair.substring(0, split)] = pair.substring(split + 1)
                }
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: run {
                        connection.disconnect()
                        throw IllegalStateException("Google Drive redirect கிடைக்கவில்லை.")
                    }
                nextUrl = URL(nextUrl, location)
                connection.disconnect()
                return@repeat
            }
            if (code !in 200..299) {
                connection.disconnect()
                if (code == 401 || code == 403) {
                    throw IllegalStateException("இந்த Drive கோப்பைப் பதிவிறக்க அனுமதி இல்லை. பகிர்வு அமைப்பையும் பதிவிறக்க அனுமதியையும் சரிபார்க்கவும்.")
                }
                throw IllegalStateException("Google Drive பதிவிறக்கம் தோல்வியடைந்தது (HTTP $code).")
            }
            val contentType = connection.contentType.orEmpty().substringBefore(';').lowercase(Locale.ROOT)
            if (contentType == "text/html") {
                val html = connection.inputStream.use { readTextLimited(it, 2 * 1024 * 1024) }
                val tokenFromCookie = cookies.entries.firstOrNull { it.key.startsWith("download_warning") }?.value
                val token = tokenFromCookie ?: findDriveConfirmationToken(html)
                connection.disconnect()
                if (token != null && !confirmationTried) {
                    confirmationTried = true
                    nextUrl = URL("https://drive.google.com/uc?export=download&confirm=" +
                        URLEncoder.encode(token, "UTF-8") + "&id=" +
                        URLEncoder.encode(fileId, "UTF-8") + driveResourceKeyQuery(originalUrl))
                    return@repeat
                }
                throw IllegalStateException("Google Drive பதிவிறக்க HTML பக்கத்தை வழங்கியது. கோப்பு Anyone with the link எனப் பகிரப்பட்டுள்ளதா, பதிவிறக்கம் அனுமதிக்கப்பட்டுள்ளதா எனச் சரிபார்க்கவும்.")
            }
            return connection
        }
        throw IllegalStateException("Google Drive நேரடி Download இணைப்பை உருவாக்க முடியவில்லை.")
    }

    private fun findDriveConfirmationToken(html: String): String? {
        val normalized = html.replace("&amp;", "&").replace("&#38;", "&")
        return Regex("""[?&]confirm=([0-9A-Za-z_-]+)""").find(normalized)?.groupValues?.getOrNull(1)
            ?: Regex("""name=["']confirm["']\s+value=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(normalized)?.groupValues?.getOrNull(1)
            ?: Regex("""download_warning[^=]*=([^;"'\s]+)""").find(normalized)?.groupValues?.getOrNull(1)
    }

    private fun isAllowedDriveHost(host: String): Boolean {
        val value = host.lowercase(Locale.ROOT).trimEnd('.')
        return value == "drive.google.com" || value == "docs.google.com" ||
            value == "drive.usercontent.google.com" || value.endsWith(".googleusercontent.com")
    }

    private fun driveResourceKeyQuery(rawUrl: String): String {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return ""
        val key = uri.rawQuery.orEmpty().split("&").firstOrNull { it.startsWith("resourcekey=") }
            ?.substringAfter("=")?.takeIf { it.isNotBlank() } ?: return ""
        return "&resourcekey=$key"
    }

    private fun parseContentDispositionFileName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val extended = Regex("""filename\*=(?:UTF-8'')?([^;]+)""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)?.trim()?.trim('"')
        val simple = Regex("""filename=["']?([^;"']+)""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)?.trim()
        val picked = extended ?: simple ?: return null
        return runCatching { java.net.URLDecoder.decode(picked, "UTF-8") }.getOrDefault(picked)
    }

    private fun safeDownloadFileName(candidate: String, mimeType: String, fileId: String): String {
        var name = candidate.substringAfterLast('/').trim()
            .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
            .trim('.', ' ')
        if (name.isBlank() || name.equals("Google Drive", true)) name = "DriveVideo-${fileId.take(8)}"
        if (!name.contains('.')) {
            name += when (mimeType.lowercase(Locale.ROOT)) {
                "video/webm" -> ".webm"
                "video/quicktime" -> ".mov"
                "video/x-matroska" -> ".mkv"
                "video/3gpp" -> ".3gp"
                else -> ".mp4"
            }
        }
        return name.take(180)
    }

    private fun findHtmlMeta(html: String, key: String): String? {
        val escaped = Regex.escape(key)
        val patterns = listOf(
            Regex("""<meta[^>]+(?:property|name)=["']$escaped["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']$escaped["']""", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }
            ?.let(::cleanDriveTitle)?.takeIf { it.isNotBlank() }
    }

    private fun findHtmlTitle(html: String): String? =
        Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(1)?.let(::cleanDriveTitle)

    private fun cleanDriveTitle(value: String?): String =
        android.text.Html.fromHtml(value.orEmpty(), android.text.Html.FROM_HTML_MODE_LEGACY)
            .toString().replace(Regex("\\s+"), " ").trim()

    private fun readTextLimited(input: InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw IllegalStateException("Google Drive பக்கத்தின் தகவல் அதிகமாக உள்ளது.")
            output.write(buffer, 0, count)
        }
        return String(output.toByteArray(), Charsets.UTF_8)
    }

    suspend fun deleteStockVideo(id: String) = withContext(Dispatchers.IO) {
        request("/rest/v1/stock_videos?id=eq." + encode(id), "DELETE", null)
    }

    suspend fun uploadNewsPhoto(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = compressPhoto(uri)
        if (bytes.isEmpty()) throw IllegalStateException("படத்தைச் சுருக்க முடியவில்லை.")
        if (bytes.size > 4 * 1024 * 1024) throw IllegalStateException("படத்தின் அளவு 4 MB-ஐ விட அதிகமாக உள்ளது.")
        val payload = JSONObject()
            .put("action", "upload")
            .put("file_base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
        val response = executeMediaFunction(payload, binaryResponse = false)
        JSONObject(String(response, Charsets.UTF_8)).optString("path").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("சேமிக்கப்பட்ட படத்தின் பாதை கிடைக்கவில்லை.")
    }

    suspend fun getNewsPhotoBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        executeMediaFunction(JSONObject().put("action", "download").put("path", path), binaryResponse = true)
    }

    suspend fun deleteNewsPhoto(path: String) = withContext(Dispatchers.IO) {
        executeMediaFunction(JSONObject().put("action", "delete").put("path", path), binaryResponse = false)
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

    private fun executeMediaFunction(payload: JSONObject, binaryResponse: Boolean, allowRefresh: Boolean = true): ByteArray {
        val connection = (URL("$baseUrl/functions/v1/news-media").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 35_000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Accept", if (binaryResponse) "image/jpeg" else "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${currentAccessToken()}")
            setRequestProperty("x-app-device-id", preferences.getString("device_id", "").orEmpty())
            setRequestProperty("x-app-device-secret", preferences.getString("device_secret", "").orEmpty())
            doOutput = true
        }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (code == 401 && allowRefresh && refreshSession()) {
                return executeMediaFunction(payload, binaryResponse, allowRefresh = false)
            }
            if (code !in 200..299) {
                throw IllegalStateException(friendlyError(String(bytes, Charsets.UTF_8), code))
            }
            bytes
        } finally {
            connection.disconnect()
        }
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
        attachmentPaths: List<String> = emptyList(),
        tags: List<String> = emptyList()
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
            .put("tags", JSONArray(tags))
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

    private fun accessTokenExpiresSoon(token: String?): Boolean {
        val payload = token?.split(".")?.getOrNull(1) ?: return false
        val decoded = runCatching {
            String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        }.getOrNull() ?: return false
        val expiry = runCatching { JSONObject(decoded).optLong("exp", 0L) }.getOrDefault(0L)
        return expiry > 0L && expiry <= System.currentTimeMillis() / 1000L + 120L
    }

    private fun request(
        path: String,
        method: String,
        body: JSONObject?,
        authenticated: Boolean = true,
        allowRefresh: Boolean = true
    ): String {
        if (authenticated && allowRefresh && accessTokenExpiresSoon(preferences.getString("access_token", null))) {
            val refreshed = synchronized(sessionRefreshLock) {
                if (accessTokenExpiresSoon(preferences.getString("access_token", null))) refreshSession() else true
            }
            if (!refreshed) {
                throw IllegalStateException(
                    "உள்நுழைவு அமர்வு புதுப்பிக்கப்படவில்லை. இணையத்தைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்; வரைவு சாதனத்தில் பாதுகாக்கப்பட்டுள்ளது."
                )
            }
        }
        val token = if (authenticated) {
            preferences.getString("access_token", null)
                ?: throw IllegalStateException("உள்நுழையவும்.")
        } else {
            null
        }
        val response = executeHttp(path, method, body, token)
        if (response.status == 401 && authenticated && allowRefresh) {
            val refreshed = synchronized(sessionRefreshLock) {
                val currentToken = preferences.getString("access_token", null)
                if (!accessTokenExpiresSoon(currentToken)) true else refreshSession()
            }
            if (refreshed) return request(path, method, body, authenticated, false)
            // A temporary network failure must not force reporters to log in again.
            throw IllegalStateException(
                "உள்நுழைவு அமர்வைப் புதுப்பிக்க முடியவில்லை. இணையத்தைச் சரிபார்த்து மீண்டும் சேமிக்கவும்; வரைவு சாதனத்தில் பாதுகாக்கப்பட்டுள்ளது."
            )
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
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
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