import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.model.*
import com.google.auth.oauth2.GoogleCredentials
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.auth.http.HttpCredentialsAdapter
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer

object PlayStorePublisher {
    @JvmStatic
    fun main(args: Array<String>) {
        fun usage() {
            println("Usage: <track> <service_account_json_path>")
            println("  <track>: internal | production | both")
            println("  <service_account_json_path>: Path to your Google service account JSON key")
        }

        if (args.size < 2) {
            usage()
            return
        }

        val trackArg = args[0]
        val serviceAccountPath = args[1]
        val tracks = when (trackArg) {
            "internal" -> listOf("internal")
            "production" -> listOf("production")
            "both" -> listOf("internal", "production")
            else -> {
                usage()
                return
            }
        }

        // Authenticate with Google Play API
        val credentials = GoogleCredentials.fromStream(File(serviceAccountPath).inputStream())
            .createScoped(listOf("https://www.googleapis.com/auth/androidpublisher"))
        val baseInitializer = HttpCredentialsAdapter(credentials)
        val requestInitializer = HttpRequestInitializer { request: HttpRequest ->
            baseInitializer.initialize(request)
            request.connectTimeout = 600_000 // 10 minutes
            request.readTimeout = 600_000 // 10 minutes
        }
        val transport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        val publisher = AndroidPublisher.Builder(transport, jsonFactory, requestInitializer)
            .setApplicationName("Immich TV (Unofficial)")
            .build()

        val packageName = "nl.giejay.android.tv.immich"
        val promote = args.size > 2 && args[2].equals("promote", ignoreCase = true)

        if (promote) {
            try {
                val edit = publisher.edits().insert(packageName, null).execute()
                val editId = edit.id
                val internalTrack = publisher.edits().tracks().get(packageName, editId, "internal").execute()
                val latestRelease = internalTrack.releases?.maxByOrNull { it.versionCodes?.maxOrNull() ?: 0L }
                if (latestRelease == null || latestRelease.versionCodes.isNullOrEmpty()) {
                    println("No internal release found to promote.")
                    return
                }
                val prodRelease = TrackRelease()
                    .setName(latestRelease.name)
                    .setVersionCodes(latestRelease.versionCodes)
                    .setStatus("completed")
                    .setReleaseNotes(latestRelease.releaseNotes)
                val prodTrack = Track().setReleases(listOf(prodRelease))
                publisher.edits().tracks().update(packageName, editId, "production", prodTrack).execute()
                publisher.edits().commit(packageName, editId).execute()
                println("Promoted internal release to production: versionCodes=${latestRelease.versionCodes}")
            } catch (e: Exception) {
                println("Error during promotion: ${e.message}")
                e.printStackTrace()
            }
            return
        }

        // Prompt for keystore password
        print("Enter keystore password: ")
        val password = System.console()?.readPassword()?.concatToString()
            ?: readLine() // fallback if not running in a console

        // Build the AAB using Gradle, passing the password as an env variable
        println("Building the Android App Bundle (AAB)...")
        val gradleProcess = ProcessBuilder("./gradlew", "bundleRelease")
            .inheritIO()
            .apply {
                environment()["RELEASE_KEYSTORE_PASSWORD"] = password ?: ""
                environment()["RELEASE_KEY_PASSWORD"] = password ?: ""
            }
            .start()

        val exitCode = gradleProcess.waitFor()
        if (exitCode != 0) {
            println("Gradle build failed with exit code $exitCode")
            return
        }
        println("AAB build completed.")

        val repo = FileRepositoryBuilder()
            .setGitDir(File(".git"))
            .readEnvironment()
            .findGitDir()
            .build()
        val git = Git(repo)
        val tags = git.tagList().call().map { it.name.substringAfterLast("/") }
        val tagPattern = Regex("v(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?")
        val parsedTags = tags.mapNotNull { tag ->
            tagPattern.matchEntire(tag)?.let { match ->
                val major = match.groupValues[1].toInt()
                val minor = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                val patch = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
                Triple(tag, major, minor * 1000 + patch) // Use minor*1000+patch for correct sorting
            }
        }
        val sortedTags = parsedTags.sortedWith(compareBy({ it.second }, { it.third })).map { it.first }
        if (sortedTags.size < 2) {
            println("Not enough tags to generate changelog.")
            return
        }
        val lastTag = sortedTags[sortedTags.size - 1]
        val prevTag = sortedTags[sortedTags.size - 2]
        val log = git.log()
            .add(repo.resolve(lastTag))
            .not(repo.resolve(prevTag))
            .call()
        val changelog = log
            .filter { !it.shortMessage.contains("Create new release", ignoreCase = true) }
            .map { "- ${it.shortMessage}" }
            .joinToString("\n")

        println("Changelog between $prevTag and $lastTag:")
        println(changelog)

        val aabPath = "app/build/outputs/bundle/release/app-release.aab"
        val aabFile = File(aabPath)
        if (!aabFile.exists()) {
            println("AAB file not found at $aabPath")
            return
        }

        // Upload AAB and release to selected tracks
        try {
            // Create a new edit
            val edit = publisher.edits().insert(packageName, null).execute()
            val editId = edit.id

            // Upload the bundle
            val fileContent = FileContent("application/octet-stream", aabFile)
            val bundle = publisher.edits().bundles().upload(packageName, editId, fileContent).execute()
            println("Uploaded bundle versionCode: ${bundle.versionCode}")

            // Assign release to tracks
            for (track in tracks) {
                val release = TrackRelease()
                    .setName("${lastTag}")
                    .setVersionCodes(listOf(bundle.versionCode.toLong()))
                    .setStatus("completed")
                    .setReleaseNotes(listOf(LocalizedText().setLanguage("en-US").setText(changelog)))
                val trackObj = Track().setReleases(listOf(release))
                publisher.edits().tracks().update(packageName, editId, track, trackObj).execute()
                println("Assigned release to $track track.")
            }

            // Commit the edit
            publisher.edits().commit(packageName, editId).execute()
            println("Successfully published to tracks: $tracks")
        } catch (e: Exception) {
            println("Error during publishing: ${e.message}")
            e.printStackTrace()
        }
    }
}
