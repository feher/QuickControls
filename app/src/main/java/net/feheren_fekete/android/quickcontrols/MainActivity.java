package net.feheren_fekete.android.quickcontrols;

import android.content.Intent;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(getApplicationContext(), R.xml.preferences, false);
        setContentView(R.layout.activity_main);
        getFragmentManager().beginTransaction().replace(R.id.main_layout, new SettingsFragment()).commit();
        startService(new Intent(this, QuickControlsService.class));
    }

}
