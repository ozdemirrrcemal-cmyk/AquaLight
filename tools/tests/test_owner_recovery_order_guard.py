import importlib.util
from pathlib import Path
import tempfile
import unittest

MODULE_PATH = Path(__file__).resolve().parents[1] / 'owner_recovery_order_guard.py'
SPEC = importlib.util.spec_from_file_location('owner_recovery_order_guard', MODULE_PATH)
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)


class OwnerRecoveryOrderGuardTest(unittest.TestCase):
    def test_incomplete_water_cutover_must_resume_before_archive_recovery(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for relative in (guard.COORDINATOR, guard.SERVICES):
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text((MODULE_PATH.parent.parent / relative).read_text())
            coordinator = root / guard.COORDINATOR
            resume = 'WaterAnalysisDataStoreManager(appContext).resumePendingCutover(normalizedOwnerUid)'
            source = coordinator.read_text().replace(resume, '')
            coordinator.write_text(source.replace('return OwnerRepairCounts(', resume + '\nreturn OwnerRepairCounts('))
            self.assertEqual(1, len(guard.violations(root)))

    def test_production_recovers_archive_before_guarded_deletion_recovery(self):
        self.assertEqual([], guard.violations(MODULE_PATH.parent.parent))

    def test_previous_services_phase_order_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for relative in (guard.COORDINATOR, guard.SERVICES):
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text((MODULE_PATH.parent.parent / relative).read_text())
            coordinator = root / guard.COORDINATOR
            recovery = 'UserDataRestoreRecovery.create(appContext, normalizedOwnerUid).recover(normalizedOwnerUid)'
            coordinator.write_text(coordinator.read_text().replace(recovery, ''))
            (root / guard.SERVICES).write_text(recovery)
            self.assertEqual(2, len(guard.violations(root)))

    def test_late_recovery_and_unbound_assignment_repository_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for relative in (guard.COORDINATOR, guard.SERVICES):
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text((MODULE_PATH.parent.parent / relative).read_text())
            coordinator = root / guard.COORDINATOR
            source = coordinator.read_text()
            recovery = 'UserDataRestoreRecovery.create(appContext, normalizedOwnerUid).recover(normalizedOwnerUid)'
            late = source.replace(recovery, '').replace('return OwnerRepairCounts(', recovery + '\nreturn OwnerRepairCounts(')
            coordinator.write_text(late)
            self.assertEqual(1, len(guard.violations(root)))
            coordinator.write_text(source.replace('TankDeviceAssignmentRepositoryProvider.get(appContext)', 'unbound'))
            self.assertEqual(1, len(guard.violations(root)))
