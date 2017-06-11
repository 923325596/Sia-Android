package vandyke.siamobile;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.ads.AdView;
import vandyke.siamobile.dialogs.RemoveAdsDialog;
import vandyke.siamobile.fragments.*;

public class MainActivity extends AppCompatActivity {

    public static SharedPreferences prefs;
    public static RequestQueue requestQueue;
    public static MainActivity instance;

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private NavigationView navigationView;
    private MenuItem activeMenuItem;

    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_layout);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        requestQueue = Volley.newRequestQueue(this);
        instance = this;

        // disabled for now because it's annoying. TODO: fully implement ads and disabled them
//        if (prefs.getBoolean("adsEnabled", true)) {
//            MobileAds.initialize(this, "ca-app-pub-3940256099942544~3347511713");
//            ((AdView)findViewById(R.id.adView)).loadAd(new AdRequest.Builder().build());
//        }
        ((AdView)findViewById(R.id.adView)).setVisibility(View.GONE);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        // set up drawer button on action bar
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        // set action stuff for when drawer items are selected
        navigationView = (NavigationView) findViewById(R.id.drawer_navigation_view);
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if (item == activeMenuItem) {
                    drawerLayout.closeDrawers();
                    return true;
                }

                if (item.getGroupId() != R.id.money_stuff) {
                    if (activeMenuItem != null)
                        activeMenuItem.setChecked(false);
                    item.setChecked(true);
                }
                activeMenuItem = item;
                drawerLayout.closeDrawers();

                switch (item.getItemId()) {
                    case R.id.drawer_item_files:
                        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_frame, new FilesFragment()).commit();
                        getSupportActionBar().setTitle("Files");
                        return true;
                    case R.id.drawer_item_wallet:
                        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_frame, new WalletFragment()).commit();
                        getSupportActionBar().setTitle("Wallet");
                        return true;
                    case R.id.drawer_item_hosting:
                        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_frame, new HostingFragment()).commit();
                        getSupportActionBar().setTitle("Hosting");
                        return true;
                    case R.id.drawer_item_terminal:
                        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_frame, new TerminalFragment()).commit();
                        getSupportActionBar().setTitle("Terminal");
                        return true;
                    case R.id.drawer_item_settings:
                        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_frame, new SettingsFragment()).commit();
                        getSupportActionBar().setTitle("Settings");
                        return true;
                    case R.id.drawer_item_about:
                        // TODO: about stuff
                        return true;
                    case R.id.drawer_item_remove_ads_fees:
                        RemoveAdsDialog.createAndShow(getSupportFragmentManager());
                        break;
                    case R.id.drawer_item_donate:
                        // TODO: donate stuff
                }
                return false;
            }
        });

        prefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                SharedPreferences.Editor editor = prefs.edit();
                switch (key) {
                    case "operationMode":
                        if (prefs.getString("operationMode", "remote_full_node").equals("remote_full_node")) {
                            editor.putString("address", prefs.getString("remoteAddress", "192.168.1.11:9980"));
                        } else if (prefs.getString("operationMode", "remote_full_node").equals("local_wallet_and_server")) {
                            editor.putString("address", "localhost:9980");
                        }
                        editor.apply();
                        break;
                    case "remoteAddress":
                        if (prefs.getString("operationMode", "remote_full_node").equals("remote_full_node")) {
                            editor.putString("address", prefs.getString("remoteAddress", "192.168.1.11:9980"));
                            editor.apply();
                        }
                        break;
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle your other action bar items...

        return super.onOptionsItemSelected(item);
    }

    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle.syncState();
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_main, menu);
        return true;
    }
}
