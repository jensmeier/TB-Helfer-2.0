"""S-06 internal SYSTEM export authentication; no Docker or NAS required."""
import unittest

from tbserver.system_backup import system_export_authorized


class SystemExportAuthorizationTests(unittest.TestCase):
    def test_does_not_accept_absent_or_weak_token(self):
        self.assertFalse(system_export_authorized(None, None))
        self.assertFalse(system_export_authorized("short", "short"))
        self.assertFalse(system_export_authorized("x" * 32, None))
        self.assertFalse(system_export_authorized("x" * 32, "x" * 31))

    def test_only_exact_shared_secret_is_accepted(self):
        secret = "this-is-only-an-isolated-unit-test-fixture"
        self.assertTrue(system_export_authorized(secret, secret))
        self.assertFalse(system_export_authorized(secret, secret + "wrong"))
        self.assertFalse(system_export_authorized(secret, secret.upper()))


if __name__ == "__main__":
    unittest.main()
