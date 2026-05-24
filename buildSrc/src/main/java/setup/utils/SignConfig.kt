package setup.utils

import com.android.build.gradle.internal.dsl.SigningConfig
import org.gradle.api.Project
import java.io.File
import java.io.FileInputStream
import java.util.*

object SignConfig {

    fun debug(project: Project, config: SigningConfig) {
        val properties = loadProperties(project) ?: return
        config.apply {
            storeFile(project.getAssembleFile(properties["KEY_LOCATION"].toString()))
            storePassword(properties["KEYSTORE_PASS"].toString())
            keyAlias(properties["ALIAS_NAME"].toString())
            keyPassword(properties["ALIAS_PASS"].toString())

            enableV1Signing = true
        }
    }

    fun release(project: Project, config: SigningConfig) {
        val properties = loadLocalProperties(project) ?: loadProperties(project)
        
        val storeFile: File
        val storePassword: String
        val keyAlias: String
        val keyPassword: String
        
        if (properties != null) {
            storeFile = properties["storeFile"]?.toString()?.let {
                if (it.startsWith("/")) File(it) else project.getAssembleFile(it)
            } ?: project.getAssembleFile("dandanplay.jks")
            
            storePassword = properties["storePassword"]?.toString() ?: System.getenv("KEYSTORE_PASS") ?: ""
            keyAlias = properties["keyAlias"]?.toString() ?: System.getenv("ALIAS_NAME") ?: ""
            keyPassword = properties["keyPassword"]?.toString() ?: System.getenv("KEY_PASSWORD") ?: ""
        } else {
            storeFile = project.getAssembleFile("dandanplay.jks")
            storePassword = System.getenv("KEYSTORE_PASS") ?: ""
            keyAlias = System.getenv("ALIAS_NAME") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
        
        config.storeFile = storeFile
        config.storePassword = storePassword
        config.keyAlias = keyAlias
        config.keyPassword = keyPassword
    }

    private fun loadLocalProperties(project: Project): Properties? {
        val propertiesFile = File(project.rootDir, "local.properties")
        if (propertiesFile.exists()) {
            val properties = Properties()
            properties.load(FileInputStream(propertiesFile))
            if (properties.containsKey("storeFile")) {
                return properties
            }
        }
        return null
    }

    private fun loadProperties(project: Project): Properties? {
        var propertiesFile = project.getAssembleFile("keystore.properties")
        if (propertiesFile.exists().not()) {
            propertiesFile = project.getAssembleFile("debug.properties")
        }
        if (propertiesFile.exists()) {
            val properties = Properties()
            properties.load(FileInputStream(propertiesFile))
            return properties
        }
        return null
    }

    private fun Project.getAssembleFile(fileName: String): File {
        return File(rootDir,"gradle/assemble/$fileName")
    }
}