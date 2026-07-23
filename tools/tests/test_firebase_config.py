import unittest

from tools.ci.materialize_firebase_config import PACKAGES, fixture, validate


class FirebaseConfigValidationTest(unittest.TestCase):
    def test_development_fixture_contains_required_client(self):
        validate(fixture("development", PACKAGES["development"]), PACKAGES["development"])

    def test_staging_fixture_contains_debug_and_release_clients(self):
        config = fixture("staging", PACKAGES["staging"])
        validate(config, PACKAGES["staging"])
        packages = {
            client["client_info"]["android_client_info"]["package_name"]
            for client in config["client"]
        }
        self.assertEqual(set(PACKAGES["staging"]), packages)

    def test_rejects_missing_production_client(self):
        config = {
            "project_info": {"project_id": "wrong-project"},
            "client": [
                {
                    "client_info": {
                        "android_client_info": {
                            "package_name": "com.aqua.aqualight.staging"
                        }
                    }
                }
            ],
        }
        with self.assertRaises(ValueError):
            validate(config, PACKAGES["production"])

    def test_rejects_incomplete_staging_matrix(self):
        config = fixture("staging", ("com.aqua.aqualight.staging",))
        with self.assertRaises(ValueError):
            validate(config, PACKAGES["staging"])


if __name__ == "__main__":
    unittest.main()
