package net.feheren_fekete.android.quickcontrols;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

public class SettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_key_big_enabled))) {
            updateScreen();
        }
        Activity activity = getActivity();
        if (activity != null) {
            Intent intent = new Intent(activity, QuickControlsService.class);
            intent.setAction(QuickControlsService.ACTION_UPDATE_NOTIFICATION);
            activity.startService(intent);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        updateScreen();
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    private void updateScreen() {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        SharedPreferences sharedPreferences = preferenceScreen.getSharedPreferences();
        boolean bigNotificationEnabled = sharedPreferences.getBoolean(getString(R.string.pref_key_big_enabled), true);
        preferenceScreen.findPreference(getString(R.string.pref_key_big_show_music_volume)).setEnabled(bigNotificationEnabled);
        preferenceScreen.findPreference(getString(R.string.pref_key_big_show_ring_volume)).setEnabled(bigNotificationEnabled);
        preferenceScreen.findPreference(getString(R.string.pref_key_big_show_notifications_volume)).setEnabled(bigNotificationEnabled);
    }

}
