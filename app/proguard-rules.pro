# kotlinx.serialization: keep generated serializers for bundled guide and map-manifest models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class app.projecthaven.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.projecthaven.**$$serializer { *; }

# MapLibre ships its own consumer rules; nothing extra is required here.
