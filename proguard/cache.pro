# Striped64 uses this
-dontwarn sun.misc.Unsafe

# Striped64 appears to make some assumptions about object layout that
# really might not be safe. This should be investigated.
-keepclassmembers class com.m3.collections.guava.cache.Striped64 {
  *** base;
  *** busy;
}
-keepclassmembers class com.m3.collections.guava.cache.Striped64$Cell {
  <fields>;
}
