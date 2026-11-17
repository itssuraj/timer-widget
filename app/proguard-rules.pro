# Proguard / R8 optimization rules for Timer Widget

# Keep model data classes used by Gson for DataStore serialization
-keepclassmembers class com.minimal.timerwidget.model.** { *; }
-keep class com.minimal.timerwidget.model.** { *; }

# Keep AppWidgetProvider, Service and BroadcastReceiver
-keep public class com.minimal.timerwidget.TimerWidgetProvider { *; }
-keep public class com.minimal.timerwidget.TimerService { *; }
-keep public class com.minimal.timerwidget.BootReceiver { *; }
