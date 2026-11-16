"""Regression guard for custom builds: upstream release code must not ship.

These are source/manifest acceptance checks, not Android UI runtime tests.
"""
import pathlib
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / 'src/com/techgenius/tvoslauncher'


class CustomUpdatesTest(unittest.TestCase):
    def test_updater_has_no_release_network_or_install_paths(self):
        source = (SRC / 'AppUpdater.java').read_text()
        for forbidden in ('https://', 'HttpURLConnection', 'PackageInstaller', 'LocalAdb',
                          'downloadAndInstall', 'showUpdateAvailableDialog', 'checkForUpdates',
                          'getSharedPreferences', 'new Thread'):
            self.assertNotIn(forbidden, source)
        self.assertIn('getCurrentVersion', source)

    def test_no_startup_check_or_settings_actions(self):
        main = (SRC / 'MainActivity.java').read_text()
        settings = (SRC / 'SettingsActivity.java').read_text()
        self.assertNotIn('AppUpdater.', main)
        calls = re.findall(r'AppUpdater\.(\w+)\(', settings)
        self.assertEqual(calls, ['getCurrentVersion'])
        self.assertIn('Custom source build', settings)

    def test_no_updater_receiver(self):
        manifest = ET.parse(ROOT / 'AndroidManifest.xml')
        names = [x.get('{http://schemas.android.com/apk/res/android}name', '')
                 for x in manifest.iter('receiver')]
        self.assertFalse(any('AppUpdater' in n for n in names))


if __name__ == '__main__':
    unittest.main()
