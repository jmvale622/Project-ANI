import unittest

from api.auth import account_document_id, hash_password, verify_password


class AccountAuthenticationTests(unittest.TestCase):
    def test_password_hash_verifies_only_matching_password(self):
        salt, password_hash = hash_password("test-pin-4821")

        self.assertTrue(verify_password("test-pin-4821", salt, password_hash))
        self.assertFalse(verify_password("wrong-pin", salt, password_hash))

    def test_each_password_hash_uses_a_unique_salt(self):
        first_salt, first_hash = hash_password("same-password")
        second_salt, second_hash = hash_password("same-password")

        self.assertNotEqual(first_salt, second_salt)
        self.assertNotEqual(first_hash, second_hash)

    def test_contact_document_id_is_case_and_whitespace_insensitive(self):
        self.assertEqual(
            account_document_id(" Farmer@example.test "),
            account_document_id("farmer@example.test"),
        )


if __name__ == "__main__":
    unittest.main()
