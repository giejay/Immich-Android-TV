# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep line numbers so Crashlytics release crash reports are still readable after obfuscation.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Gson uses reflection to read/write these fields by name, so field names and no-arg
# constructors must survive R8 or (de)serialization silently breaks at runtime.
-keep class nl.giejay.android.tv.immich.api.model.** { *; }
-keep class nl.giejay.android.tv.immich.api.service.** { *; }

# MetaDataSerializer (mediaslider module) delegates to Gson's default reflective adapter
# for these classes and stores/reads the result as JSON in SharedPreferences, so their
# field names must stay stable across obfuscation.
-keep class nl.giejay.mediaslider.adapter.MetaDataItem { *; }
-keep class * extends nl.giejay.mediaslider.adapter.MetaDataItem { *; }

# MediaSliderView swaps this field in by name via reflection to control slide animation speed.
-keepclassmembers class androidx.viewpager.widget.ViewPager {
    android.widget.Scroller mScroller;
}

# Pref/PrefScreen objects are enumerated via Kotlin sealedSubclasses reflection, their class
# simple names double as SharedPreferences keys (see Pref.key()), and MetaDataScreen is resolved
# by fully-qualified name from nav_graph.xml's argType -- all of this breaks silently under R8
# renaming/shrinking without keeping the whole package intact.
-keep class nl.giejay.android.tv.immich.shared.prefs.** { *; }