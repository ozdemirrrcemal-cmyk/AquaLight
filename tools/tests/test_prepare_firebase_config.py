import base64
import json
import unittest

from tools.prepare_firebase_config import TARGETS, decode_secret, placeholder_config, validate_config


class FirebaseConfigTest(unittest.TestCase):
    def test_placeholders_match_each_application_id(self):
        for target in TARGETS.values():
            with self.subTest(environment=target.name):
                payload = placeholder_config(target)
                project_id = validate_config(payload, target)
                self.assertTrue(project_id.startswith("aqualight-ci-"))

    def test_base64_json_decode(self):
        payload = placeholder_config(TARGETS["debug"])
        encoded = base64.b64encode(json.dumps(payload).encode()).decode()
        decoded = decode_secret(encoded, "TEST_SECRET")
        self.assertEqual(payload, decoded)

    def test_wrong_package_is_rejected(self):
        payload = placeholder_config(TARGETS["debug"])
        with self.assertRaises(ValueError):
            validate_config(payload, TARGETS["production"])


if __name__ == "__main__":
    unittest.main()
