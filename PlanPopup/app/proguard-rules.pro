# Keep Room entities and database metadata.
-keep class com.planpop.app.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** create(android.content.Context);
}
