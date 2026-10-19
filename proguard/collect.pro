# The nested FieldSettersHolder class looks these up.
#
# We use -keepclassmembernames because we want for ImmutableMultimap and its
# fields to be stripped if it's unused: -keepclassmembernames says that, *if*
# you're keeping the fields, you need to leave their names untouched. (Anyone
# who is using ImmutableMultimap will certainly be using its fields. So we
# don't need to worry that an ImmutableMultimap user will have the fields
# optimized away.)
#
# This configuration is untested....
-keepclassmembernames class com.m3.collections.guava.collect.ImmutableMultimap {
  *** map;
  *** size;
}
# similarly:
-keepclassmembernames class com.m3.collections.guava.collect.ConcurrentHashMultiset {
  *** countMap;
}
# similarly:
-keepclassmembernames class com.m3.collections.guava.collect.ImmutableSetMultimap {
  *** emptySet;
}
# similarly:
-keepclassmembernames class com.m3.collections.guava.collect.AbstractSortedMultiset {
  *** comparator;
}
# similarly:
-keepclassmembernames class com.m3.collections.guava.collect.TreeMultiset {
  *** range;
  *** rootReference;
  *** header;
}
