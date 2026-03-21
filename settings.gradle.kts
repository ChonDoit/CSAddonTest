rootProject.name = "CloudstreamPlugins"

val disabled = listOf("build", "gradle", "app") // Add folders to skip here

/**
 * Recursively finds and includes Gradle projects.
 */
fun includeProjects(dir: File) {
    dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { subDir ->
        if (disabled.contains(subDir.name)) return@forEach

        if (File(subDir, "build.gradle.kts").exists()) {
            // Calculate the path relative to the root (e.g., "movies/provider1")
            val projectPath = subDir.relativeTo(rootDir).path.replace(File.separator, ":")
            include(":$projectPath")
        } else {
            // If no build file here, look deeper into the sub-directory
            includeProjects(subDir)
        }
    }
}

includeProjects(rootDir)
