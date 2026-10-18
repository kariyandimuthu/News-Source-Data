package in.newssourcedata.app

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "news")
data class NewsItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val headline: String,
    val body: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "assignments")
data class AssignmentItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "contacts")
data class ContactItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val designation: String = ""
)

@Dao
interface NewsDao {
    @Query("SELECT * FROM news ORDER BY createdAt DESC")
    fun observeNews(): Flow<List<NewsItem>>
    @Insert suspend fun insertNews(item: NewsItem)
    @Delete suspend fun deleteNews(item: NewsItem)

    @Query("SELECT * FROM assignments ORDER BY createdAt DESC")
    fun observeAssignments(): Flow<List<AssignmentItem>>
    @Insert suspend fun insertAssignment(item: AssignmentItem)
    @Delete suspend fun deleteAssignment(item: AssignmentItem)

    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE")
    fun observeContacts(): Flow<List<ContactItem>>
    @Insert suspend fun insertContact(item: ContactItem)
    @Delete suspend fun deleteContact(item: ContactItem)
}

@Database(
    entities = [NewsItem::class, AssignmentItem::class, ContactItem::class],
    version = 1,
    exportSchema = true
)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun dao(): NewsDao
    companion object {
        @Volatile private var INSTANCE: NewsDatabase? = null
        fun get(context: Context): NewsDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NewsDatabase::class.java,
                    "news_source_data.db"
                ).build().also { INSTANCE = it }
            }
    }
}
