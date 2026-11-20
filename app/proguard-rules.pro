# kotlinx.serialization: @Serializable-Klassen behalten (Export/Import, QR-Code)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class de.dritteliga.stickertracker.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class de.dritteliga.stickertracker.**$$serializer { *; }
