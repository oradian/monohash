-injars target\monohash-0.8.0-SNAPSHOT.jar
-outjars target\monohash-0.8.0-SNAPSHOT-pro.jar

-libraryjars 'C:\Users\Oradian\.jabba\jdk\adopt@1.8.0-272\jre\lib\rt.jar'

-target 1.8
-optimizationpasses 9
-dontobfuscate
-verbose
#-skipnonpubliclibraryclasses
-dontoptimize
-dontshrink
# -keep

-keepclasseswithmembers public class * {
    public static void main(java.lang.String[]);
}
