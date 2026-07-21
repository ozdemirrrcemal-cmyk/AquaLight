package com.aqua.aqualight.platform.media

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage9CommercialMediaArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun rollbackAndSupersededDeletionRemainDurablyRecoverable() {
        val storage = source(
            "app/src/main/java/com/aqua/aqualight/platform/media/AppMediaStorage.kt"
        )
        val recovery = source(
            "app/src/main/java/com/aqua/aqualight/data/media/AppMediaRecoveryManager.kt"
        )

        listOf(
            "app_media_deletion_v1",
            "deleteAfterCommit",
            "reconcilePendingDeletions",
            "reconcileUnreferencedCommittedMedia",
            "if (deleted) removePendingEntries"
        ).forEach { token ->
            assertTrue("Durable media recovery contract missing: $token", storage.contains(token))
        }
        assertTrue(recovery.contains("reconcilePendingDeletions"))
        assertTrue(recovery.contains("reconcileUnreferencedCommittedMedia"))
    }

    @Test
    fun tankDuplicationPerformsNoFilesystemSideEffectInsideDataStoreTransform() {
        val manager = source(
            "app/src/main/java/com/aqua/aqualight/data/aquarium/store/" +
                "AquariumTankDataStoreManager.kt"
        )
        val duplicate = manager.substringBetween(
            "suspend fun duplicateTank(",
            "suspend fun deleteTanks("
        )
        val firstUpdate = duplicate.indexOf("aquariumTanksDataStore.updateData")
        val copy = duplicate.indexOf("AppMediaStorage.copyInternalMedia")

        assertTrue("Duplicate photo must be prepared before DataStore.updateData.", copy >= 0)
        assertTrue("DataStore update must exist.", firstUpdate > copy)
        val transform = duplicate.substring(firstUpdate)
        assertFalse(
            "DataStore transform must not perform media copy.",
            transform.contains("copyInternalMedia")
        )
        assertTrue(duplicate.contains("rollbackPendingMedia"))
        assertTrue(duplicate.contains("sourceTank.photoUri == sourcePhotoAtPreparation"))
    }

    @Test
    fun repositoriesOwnDurableCommitAndCoordinatorOnlyAcknowledgesIt() {
        val profile = source(
            "app/src/main/java/com/aqua/aqualight/data/user/DefaultUserProfileOperations.kt"
        )
        val tank = source(
            "app/src/main/java/com/aqua/aqualight/data/aquarium/" +
                "DefaultAquariumTankOperations.kt"
        )
        val coordinator = source(
            "app/src/main/java/com/aqua/aqualight/ui/common/media/" +
                "MediaFlowCoordinatorViewModel.kt"
        )

        assertTrue(profile.contains("deleteAfterCommit"))
        assertTrue(tank.contains("deleteAfterCommit"))
        val commitSelection = coordinator.substringBetween(
            "suspend fun commitSelection(",
            "suspend fun rollbackSelection("
        )
        assertTrue(commitSelection.contains("if (deletePersistedMedia)"))
        assertTrue(commitSelection.contains("updateSelection"))
        assertFalse(
            "Repository-owned commit acknowledgement must not perform media I/O when disabled.",
            commitSelection.substringAfter("if (deletePersistedMedia)")
                .substringAfter("}")
                .contains("AppMediaStorage")
        )
    }

    @Test
    fun feedbackSubmissionUsesOneAuthenticatedTextPersistenceBoundary() {
        val application = source(
            "app/src/main/java/com/aqua/aqualight/application/feedback/" +
                "FeedbackSubmissionOperations.kt"
        )
        val repository = source(
            "app/src/main/java/com/aqua/aqualight/data/feedback/" +
                "FirebaseFeedbackSubmissionOperations.kt"
        )
        val viewModel = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/settings/feedback/FeedbackViewModel.kt"
        )

        assertTrue(application.contains("FeedbackSubmissionPolicy"))
        assertTrue(repository.contains("FeedbackSubmissionFailureKind.AUTHENTICATION"))
        assertTrue(repository.contains("documentStore.save(documentId, data)"))
        assertTrue(viewModel.contains("FeedbackSubmissionPolicy"))
        listOf("FirebaseStorage", "journalStore", "mediaProcessor", "cleanupOrphans", "anonymous").forEach { token ->
            assertFalse("Text feedback contains obsolete dependency: $token", application.contains(token))
            assertFalse("Text feedback contains obsolete dependency: $token", repository.contains(token))
            assertFalse("Text feedback contains obsolete dependency: $token", viewModel.contains(token))
        }
    }

    @Test
    fun productionMediaApiIsDomainNeutralWithoutCompatibilityShims() {
        val processor = source(
            "app/src/main/java/com/aqua/aqualight/platform/media/ImageMediaProcessor.kt"
        )
        val container = source(
            "app/src/main/java/com/aqua/aqualight/composition/AppContainer.kt"
        )
        val providerPaths = source("app/src/main/res/xml/file_paths.xml")

        listOf(
            "interface ImageMediaProcessor",
            "class AndroidImageMediaProcessor",
            "data class ProcessedImageMedia",
            "sealed interface ImageMediaProcessingResult"
        ).forEach { token ->
            assertTrue("Generic media contract missing: $token", processor.contains(token))
        }
        assertTrue(container.contains("val imageMediaProcessor: ImageMediaProcessor"))
        assertTrue(providerPaths.contains("image_processing"))

        listOf(
            "FeedbackMediaProcessor",
            "AndroidFeedbackMediaProcessor",
            "feedbackMediaProcessor",
            "feedback_media",
            "feedback_output_",
            "typealias ImageMedia"
        ).forEach { token ->
            assertFalse("Legacy media compatibility token remains: $token", processor.contains(token))
            assertFalse("Legacy media compatibility token remains: $token", container.contains(token))
            assertFalse("Legacy media compatibility token remains: $token", providerPaths.contains(token))
        }
    }

    private fun source(relativePath: String): String =
        File(repositoryRoot, relativePath).readText()

    private fun String.substringBetween(start: String, end: String): String {
        val startIndex = indexOf(start)
        require(startIndex >= 0) { "Missing source marker: $start" }
        val endIndex = indexOf(end, startIndex + start.length)
        require(endIndex > startIndex) { "Missing source marker: $end" }
        return substring(startIndex, endIndex)
    }

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
