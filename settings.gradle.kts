rootProject.name = "CloudstreamPlugins"

// 1. Define your categories clearly
val categories = listOf(
    "Anime",
    "Cartoons",
    "Doramas",
    "IPTV",
    "Streaming",
    "Torrents"
)

// 2. Loop through each category folder
categories.forEach { category ->
    val categoryDir = File(rootDir, category)
    
    if (categoryDir.exists() && categoryDir.isDirectory) {
        // 3. Find every provider folder inside that category
        categoryDir.listFiles()
            ?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
            ?.forEach { provider ->
                
                // This creates the module ":Anime:AnimeAV"
                val projectPath = ":${category}:${provider.name}"
                include(projectPath)
                
                // Tell Gradle exactly where the folder is located
                project(projectPath).projectDir = provider
            }
    }
}