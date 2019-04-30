package com.didiglobal.booster.task.permission

import com.android.SdkConstants
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.AAR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.didiglobal.booster.gradle.scope
import com.didiglobal.booster.kotlinx.RESET
import com.didiglobal.booster.kotlinx.YELLOW
import com.didiglobal.booster.kotlinx.ifNotEmpty
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

internal open class PermissionExtractor : DefaultTask() {

    private val factory = SAXParserFactory.newInstance()

    lateinit var variant: BaseVariant

    init {
        factory.isXIncludeAware = false
        factory.isNamespaceAware = true
        factory.setFeature("http://xml.org/sax/features/namespace-prefixes", true)
        factory.setFeature("http://xml.org/sax/features/xmlns-uris", true)
        factory.isValidating = false
    }

    @TaskAction
    fun run() {
        variant.scope.getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, AAR).files.forEach { aar ->
            ZipFile(aar).use { zip ->
                zip.getEntry(SdkConstants.FN_ANDROID_MANIFEST_XML)?.let { entry ->
                    zip.getInputStream(entry).use { source ->
                        PermissionUsageHandler().also { handler ->
                            factory.newSAXParser().parse(source, handler)
                        }.permissions.sorted().ifNotEmpty { permissions ->
                            println("${aar.componentId} [$YELLOW${variant.name}$RESET]")
                            permissions.forEach { permission ->
                                println("  - $permission")
                            }
                        }
                    }
                }
            }
        }
    }

}

private val HEX = "[a-zA-Z0-9]+".toRegex()

private val EXTRA_ANDROID_M2REPOSITORY = "${File.separator}extras${File.separator}android${File.separator}m2repository${File.separator}"
private val LEN_EXTRA_ANDROID_M2REPOSITORY = EXTRA_ANDROID_M2REPOSITORY.length

private val EXTRA_GOOGLE_M2REPOSITORY = "${File.separator}extras${File.separator}google${File.separator}m2repository${File.separator}"
private val LEN_EXTRA_GOOGLE_M2REPOSITORY = EXTRA_GOOGLE_M2REPOSITORY.length

internal val File.componentId: String
    get() {
        val parent = this.parentFile
        if (parent.name.matches(HEX)) {
            val version = parent.parentFile
            val artifact = version.parentFile
            val group = artifact.parentFile
            return "${group.name}:${artifact.name}:${version.name}"
        }

        this.absolutePath.let {
            val idxAndroidM2 = it.indexOf(EXTRA_ANDROID_M2REPOSITORY)
            if (idxAndroidM2 > -1) {
                val artifact = parent.parentFile
                val group = artifact.parentFile.absolutePath.substring(idxAndroidM2 + LEN_EXTRA_ANDROID_M2REPOSITORY).replace(File.separatorChar, '.')
                return "$group:${artifact.name}:${parent.name}"
            }

            val idxGoogleM2 = it.indexOf(EXTRA_GOOGLE_M2REPOSITORY)
            if (idxGoogleM2 > -1) {
                val artifact = parent.parentFile
                val group = artifact.parentFile.absolutePath.substring(idxGoogleM2 + LEN_EXTRA_GOOGLE_M2REPOSITORY).replace(File.separatorChar, '.')
                return "$group:${artifact.name}:${parent.name}"
            }

            TODO("Unrecognizable AAR: $absolutePath")
        }
    }
