"""Run with python3 -m unittest discover -s deploy -p 'test_*.py'."""
import json
import pathlib
import subprocess
import sys
import unittest

CHECK = pathlib.Path(__file__).with_name('preflight-release.sh').read_text(encoding='utf-8').split("<<'PY'\n", 1)[1].split('\nPY\n', 1)[0]

class PreflightTests(unittest.TestCase):
    def config(self):
        return dict(SPRING_PROFILES_ACTIVE='prod', ADMIN_INITIAL_PASSWORD='private-test-password',
                    AUTH_EMAIL_ENABLED='true', GEETEST_ENABLED='true', AUTH_CODE_SECRET='x'*40,
                    MAIL_FROM='sender@example.test', TENCENTCLOUD_SECRET_ID='private-id',
                    TENCENTCLOUD_SECRET_KEY='private-key', GEETEST_CAPTCHA_ID='captcha-id',
                    GEETEST_CAPTCHA_KEY='captcha-key', SES_REGION='ap-hongkong', SES_TEMPLATE_ID='219249')

    def run_check(self, env, existing='0'):
        return subprocess.run([sys.executable, '-c', CHECK, existing],
                              input=json.dumps({'services': {'backend': {'environment': env}}}),
                              capture_output=True, text=True)

    def test_complete_configuration_does_not_print_secrets(self):
        result = self.run_check(self.config())
        self.assertEqual(result.returncode, 0, result.stderr)
        for value in ['private-test-password', 'private-id', 'private-key', 'captcha-key', 'x'*40]:
            self.assertNotIn(value, result.stdout + result.stderr)

    def test_missing_ses_credentials_blocks_release(self):
        env = self.config()
        del env['TENCENTCLOUD_SECRET_KEY']
        self.assertEqual(self.run_check(env).returncode, 1)

    def test_multibyte_password_over_bcrypt_limit_is_rejected(self):
        env = self.config()
        env['ADMIN_INITIAL_PASSWORD'] = '\u6d59' * 25
        self.assertEqual(self.run_check(env).returncode, 1)

    def test_existing_admin_does_not_require_initial_password_again(self):
        env = self.config()
        del env['ADMIN_INITIAL_PASSWORD']
        self.assertEqual(self.run_check(env, '1').returncode, 0)

    def test_disabled_email_is_not_mistaken_for_complete_registration(self):
        env = self.config()
        env['AUTH_EMAIL_ENABLED'] = 'false'
        self.assertEqual(self.run_check(env).returncode, 1)

if __name__ == '__main__':
    unittest.main()
