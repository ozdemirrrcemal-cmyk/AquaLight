"""Archive rollback must settle before deletion recovery or orphan repair can run."""
from pathlib import Path
import re

COORDINATOR = 'app/src/main/java/com/aqua/aqualight/data/auth/OwnerSessionCoordinator.kt'
SERVICES = 'app/src/main/java/com/aqua/aqualight/data/auth/SessionBoundServiceManager.kt'


def violations(root: Path) -> list[str]:
    source = (root / COORDINATOR).read_text()
    body = source.split('private suspend fun repairOwnerData(', 1)[-1]
    body = re.sub(r'\s+', '', body.split('private suspend fun abortTransition', 1)[0])
    ordered = (
        'TankDeviceAssignmentRepositoryProvider.get(appContext)',
        'WaterAnalysisDataStoreManager(appContext).resumePendingCutover(normalizedOwnerUid)',
        'UserDataRestoreRecovery.create(appContext,normalizedOwnerUid).recover(normalizedOwnerUid)',
        'assignmentRepository.repairOwnerAssignments()',
        'TankCareIntegrityRecovery',
        'repairOrphanedTankAnalyses(normalizedOwnerUid)',
    )
    positions = [body.find(token) for token in ordered]
    errors = []
    if any(position < 0 for position in positions) or positions != sorted(positions):
        errors.append(f'{COORDINATOR}: bind repository, resume water cutover, recover archive, then repair dependent data')
    if 'UserDataRestoreRecovery' in (root / SERVICES).read_text():
        errors.append(f'{SERVICES}: archive recovery cannot wait until services start after data repair')
    return errors
