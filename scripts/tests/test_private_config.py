"""Synthetic credential checks; no private profile values are read."""
import pathlib
import subprocess
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / 'check-private-config.py'


class PrivateConfigTest(unittest.TestCase):
    def run_check(self, source):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            subprocess.run(['git', 'init', '-q', directory], check=True)
            file = root / 'module/src/main/java/Example.java'
            file.parent.mkdir(parents=True)
            file.write_text(source)
            subprocess.run(['git', '-C', directory, 'add', '.'], check=True)
            return subprocess.run(['python3', str(SCRIPT), '--working-tree'], cwd=root,
                                  capture_output=True, text=True)

    def test_constructor_secret_is_rejected_without_echo(self):
        literal = 'SYNTHETIC_ONLY_' + 'a' * 32
        result = self.run_check('new DatasourcePasswordCipher("' + literal + '");')
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn(literal, result.stdout + result.stderr)
        self.assertIn('value redacted', result.stderr)

    def test_named_password_literal_is_rejected_without_echo(self):
        literal = 'synthetic-password-42'
        result = self.run_check('String password = "' + literal + '";')
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn(literal, result.stdout + result.stderr)

    def test_runtime_constructor_is_allowed(self):
        self.assertEqual(0, self.run_check('new DatasourcePasswordCipher(properties.key());').returncode)


if __name__ == '__main__':
    unittest.main()
