package br.com.amamentabebe.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FeedingType { QUICK, BREAST, BOTTLE }
enum class BreastSide { NONE, LEFT, RIGHT, BOTH }

@Entity(tableName = "feedings")
data class Feeding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMillis: Long,
    val type: FeedingType = FeedingType.QUICK,
    val side: BreastSide = BreastSide.NONE,
    val amountMl: Int? = null,
    val note: String = ""
)
