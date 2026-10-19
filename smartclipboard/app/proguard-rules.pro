# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep JSON backup field names stable if models are ever reflected.
-keepclassmembers class com.clipvault.smartclipboard.data.backup.** { *; }
