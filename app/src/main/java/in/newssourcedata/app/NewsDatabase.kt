package in.newssourcedata.app

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "news_items")
data class NewsItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val details: String,
    val district: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "assignments")
data class Assignment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val details: String,
    val dueDate: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val notes: String = ""
)

@Dao
interface NewsDao {
    @Insert
    suspend fun insertNews(item: NewsItem)

    @Query("SELECT * FROM news_items ORDER BY createdAt DESC")
    suspend fun getNews(): List<NewsItem>

    @Insert
    suspend fun insertAssignment(item: Assignment)

    @Query("SELECT * FROM assignments ORDER BY createdAt DESC")
    suspend fun getAssignments(): List<Assignment>

    @Insert
    suspend fun insertContact(item: Contact)

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    suspend fun getContacts(): List<Contact>
}

@Database(
    entities = [NewsItem::class, Assignment::class, Contact::class],
    version = 1,
    exportSchema = false
)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun newsDao(): NewsDao

    companion object {
        @Volatile private var INSTANCE: NewsDatabase? = null

        fun getInstance(context: Context): NewsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NewsDatabase::class.java,
                    "news_source_data.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
