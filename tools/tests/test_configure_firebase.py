from __future__ import annotations

import base64
import json
import os
import tempfile
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
from unittest.mock import patch

from tools.configure_firebase import FirebaseConfigError, materialize_from_environment, validate_config


def config(project_id: str, package_name: str) -> dict:
    return {
        "project_info": {"project_number": "100000000001", "project_id": project_id},
        "client": [
            {
                "client_info": {
                    "mobilesdk_app_id": "1:100000000001:android:0000000000000000000001",
                    "android_client_info": {"package_name": package_name},
                },
                "oauth_client": [
                    {
                        "client_id": "100000000001-demo.apps.googleusercontent.com",
                        "client_type": 3,
                    }
                ],
                "api_key": [{"current_key": "AIzaSyDemoOnlyNotAProductionCredential000"}],
            }
        ],
    }


class FirebaseConfigTest(unittest.TestCase):
    def test_accepts_demo_debug_and_rejects_demo_production(self) -> None:
        payload = config("demo-aqualight-debug", "com.aqua.aqualight.debug")
        self.assertEqual(
            "demo-aqualight-debug",
            validate_config(
                payload, environment="debug", package_name="com.aqua.aqualight.debug"
            ),
        )
        with self.assertRaises(FirebaseConfigError):
            validate_config(payload, environment="production", package_name="com.aqua.aqualight.debug")

    def test_requires_exact_package(self) -> None:
        with self.assertRaises(FirebaseConfigError):
            validate_config(
                config("demo-aqualight-debug", "com.example.other"),
                environment="debug",
                package_name="com.aqua.aqualight.debug",
            )

    def test_materializes_valid_production_secret_with_private_permissions(self) -> None:
        payload = config("aqualight-production", "com.aqua.aqualight")
        encoded = base64.b64encode(json.dumps(payload).encode()).decode()
        with tempfile.TemporaryDirectory() as directory, patch.dict(
            os.environ, {"FIREBASE_TEST_BASE64": encoded}, clear=False
        ):
            output = Path(directory) / "google-services.json"
            project_id = materialize_from_environment(
                environment_variable="FIREBASE_TEST_BASE64",
                output=output,
                environment="production",
                package_name="com.aqua.aqualight",
            )
            self.assertEqual("aqualight-production", project_id)
            self.assertEqual(0o600, output.stat().st_mode & 0o777)


if __name__ == "__main__":
    unittest.main()
