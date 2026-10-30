buildscript {
    extra["kotlin_version"] = "1.9.24"
    extra["room_version"] = "2.6.1"
    extra["hilt_version"] = "2.48.1"
    extra["retrofit_version"] = "2.11.0"
    extra["okhttp_version"] = "4.12.0"
    extra["coroutines_version"] = "1.8.0"
    extra["lifecycle_version"] = "2.7.0"
    extra["wear_version"] = "1.3.1"
    extra["coil_version"] = "2.7.0"
    extra["navigation_version"] = "2.7.7"
    extra["datastore_version"] = "1.1.1"
    extra["workmanager_version"] = "2.9.0"
}

allprojects {
    group = "com.treinos.app"
    version = "1.0.0-SNAPSHOT"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}