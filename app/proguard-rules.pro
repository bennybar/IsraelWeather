# kotlinx.serialization: keep generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }

# Glance creates widget action callbacks by reflection (no-arg constructor).
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
