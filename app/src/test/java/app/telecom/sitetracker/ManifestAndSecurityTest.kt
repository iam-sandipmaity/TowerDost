package app.telecom.sitetracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestAndSecurityTest {

    @Test
    fun testManifestContainsZeroNetworkAndBackupDisabled() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        val manifestContent = if (manifestFile.exists()) {
            manifestFile.readText()
        } else {
            // Check fallback path depending on test working directory
            File("app/src/main/AndroidManifest.xml").readText()
        }

        // 1. Zero Network: Manifest MUST NOT declare INTERNET permission
        assertFalse(
            "AndroidManifest must NEVER declare android.permission.INTERNET",
            manifestContent.contains("android.permission.INTERNET")
        )

        // 2. Backup Disabled
        assertTrue(
            "AndroidManifest must declare android:allowBackup=\"false\"",
            manifestContent.contains("android:allowBackup=\"false\"")
        )

        // 3. No Camera Permission (must use TakePicture intent via FileProvider)
        assertFalse(
            "AndroidManifest must not declare android.permission.CAMERA",
            manifestContent.contains("android.permission.CAMERA")
        )

        // 4. Data extraction rules configured
        assertTrue(
            "AndroidManifest must reference data_extraction_rules",
            manifestContent.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\"")
        )
        assertTrue(
            "AndroidManifest must reference backup_rules",
            manifestContent.contains("android:fullBackupContent=\"@xml/backup_rules\"")
        )
    }

    @Test
    fun testBackupRulesExcludeAllDomains() {
        val backupRulesFile = File("src/main/res/xml/backup_rules.xml").let {
            if (it.exists()) it else File("app/src/main/res/xml/backup_rules.xml")
        }
        val rulesText = backupRulesFile.readText()
        assertTrue(rulesText.contains("<exclude domain=\"root\" path=\".\" />"))
        assertTrue(rulesText.contains("<exclude domain=\"file\" path=\".\" />"))
        assertTrue(rulesText.contains("<exclude domain=\"database\" path=\".\" />"))
    }

    @Test
    fun testDataExtractionRulesExcludeCloudAndTransfer() {
        val extractionRulesFile = File("src/main/res/xml/data_extraction_rules.xml").let {
            if (it.exists()) it else File("app/src/main/res/xml/data_extraction_rules.xml")
        }
        val rulesText = extractionRulesFile.readText()
        assertTrue(rulesText.contains("<cloud-backup>"))
        assertTrue(rulesText.contains("<device-transfer>"))
    }
}
