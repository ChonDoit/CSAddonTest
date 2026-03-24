rootProject.name = "CloudstreamPlugins"

val categories = listOf(
    "Anime",
    "Cartoons",
    "Doramas",
    "IPTV",
    "Streaming",
    "Torrents"
)

categories.forEach { category ->
    val categoryDir = File(rootDir, category)
    
    if (categoryDir.exists() && categoryDir.isDirectory) {
        categoryDir.listFiles()
            ?.filter { provider -> 
                provider.isDirectory && 
                provider.name != category &&
                File(provider, "build.gradle.kts").exists() 
            }
            ?.forEach { provider ->
                val projectPath = ":${category}:${provider.name}"
                include(projectPath)
                project(projectPath).projectDir = provider
            }
    }
}
