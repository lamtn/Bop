package masysdio.masplayerpre.Activities;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.checkbox.MaterialCheckBox;

import masysdio.masplayerpre.Adapters.ThemeAdapter;
import masysdio.masplayerpre.BopUtils.ExtraUtils;
import masysdio.masplayerpre.BopUtils.ThemeUtil;
import masysdio.masplayerpre.R;
import masysdio.masplayerpre.apppurchaseutil.IabBroadcastReceiver;
import masysdio.masplayerpre.apppurchaseutil.IabHelper;
import masysdio.masplayerpre.apppurchaseutil.IabResult;
import masysdio.masplayerpre.apppurchaseutil.Inventory;
import masysdio.masplayerpre.apppurchaseutil.Purchase;
import masysdio.masplayerpre.fragments.AdvancedSettings;
import masysdio.masplayerpre.playerMain.Main;
import masysdio.masplayerpre.settings.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SettingActivity extends BaseActivity implements AdvancedSettings.OnFragmentInteractionListener, IabBroadcastReceiver.IabBroadcastListener {
    static final String TAG = "SettingActivity";
    static final String SKU_NO_ADS = "no_ads";
    static final String SKU_TEST_PURCHASED = "android.test.purchased";
    static final String SKU_TEST_CANCELED = "android.test.canceled";
    static final String SKU_TEST_ITEM_UNAVAILABLE = "android.test.item_unavailable";
    static final String SKU_TO_BUY = SKU_NO_ADS;
    // (arbitrary) request code for the purchase flow
    static final int RC_REQUEST = 10001;

    public static List<Theme> mThemeList = new ArrayList<>();
    public static int selectedTheme = 0;
    TextView tvNoAds;
    LinearLayout mode, theme;
    MaterialCheckBox pauseHeadphoneUnplugged, resumeHeadphonePlugged, headphoneControl, saveRecent, savePlaylist, saveCount, cbNoAds;
    LinearLayout llBottomSheet;
    private RecyclerView mRecyclerView;
    private ThemeAdapter mAdapter;
    private BottomSheetBehavior mBottomSheetBehavior;

    // Does the user have the premium upgrade?
    private boolean mIsNoAds = false;
    // The helper object
    IabHelper mHelper;

    // Provides purchase notification while this app is running
    IabBroadcastReceiver mBroadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(ExtraUtils.getThemedIcon(this, getDrawable(R.drawable.ic_backarrow)));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

        mode = findViewById(R.id.settingsMode);
        theme = findViewById(R.id.settingsTheme);
        pauseHeadphoneUnplugged = findViewById(R.id.pauseHeadphoneUnplugged);
        resumeHeadphonePlugged = findViewById(R.id.resumeHeadphonePlugged);
        headphoneControl = findViewById(R.id.headphoneControl);
        saveRecent = findViewById(R.id.saveRecent);
        saveCount = findViewById(R.id.saveCount);
        savePlaylist = findViewById(R.id.savePlaylist);

        llBottomSheet = findViewById(R.id.bottom_sheet);
        mBottomSheetBehavior = BottomSheetBehavior.from(llBottomSheet);
        mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        tvNoAds = findViewById(R.id.tvNoAds);
        cbNoAds = findViewById(R.id.cbNoAds);

        setupCheckBoxes();
        setListeners();

        setupInAppPurchase();
    }

    // We're being destroyed. It's important to dispose of the helper here!
    @Override
    public void onDestroy() {
        super.onDestroy();

        // very important:
        if (mBroadcastReceiver != null) {
            unregisterReceiver(mBroadcastReceiver);
        }

        // very important:
        Log.d(TAG, "Destroying helper.");
        if (mHelper != null) {
            mHelper.disposeWhenFinished();
            mHelper = null;
        }
    }

    private void setupInAppPurchase() {
        /* base64EncodedPublicKey should be YOUR APPLICATION'S PUBLIC KEY
         * (that you got from the Google Play developer console). This is not your
         * developer public key, it's the *app-specific* public key.
         *
         * Instead of just storing the entire literal string here embedded in the
         * program,  construct the key at runtime from pieces or
         * use bit manipulation (for example, XOR with some other string) to hide
         * the actual key.  The key itself is not secret information, but we don't
         * want to make it easy for an attacker to replace the public key with one
         * of their own and then fake messages from the server.
         */
        String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApsuLwq5bp4UxlqD7JSbvL8MScNPeb5TYn/ROT/YSaGy+2T7hSH621cqzPNI4cKP01vf5Vgn6QonkAYc1oye46m3hZNOFvaM4gMX2yQdOvq7wbvN6mrpnQlimHwCnt1epQprguwpnTKb9zQwsYQmgqe+eTrBtHbThKZvan4uNeJxvhRW1/L7f8UX4Y9QRHOCxZNC6PE1tqnvMKr1nYlrJS6ztVqFOS8bX2fi2hbBFytFcF+QinO5i11+iVa/OaoQrl2ICqhOaxgbHLDK33GyWXWV5Y2BWaFVXRiLFkxbCV68ydgmkod9nmLYJ1qMCYGTihRKYmOPiVyJdFWOrcSvPDQIDAQAB";

        // Some sanity checks to see if the developer (that's you!) really followed the
        // instructions to run this sample (don't put these checks on your app!)
        if (base64EncodedPublicKey.contains("CONSTRUCT_YOUR")) {
            throw new RuntimeException("Please put your app's public key in MainActivity.java. See README.");
        }
        if (getPackageName().startsWith("com.example")) {
            throw new RuntimeException("Please change the sample's package name! See README.");
        }

        // Create the helper, passing it our context and the public key to verify signatures with
        Log.d(TAG, "Creating IAB helper.");
        mHelper = new IabHelper(this, base64EncodedPublicKey);

        // enable debug logging (for a production application, you should set this to false).
        mHelper.enableDebugLogging(true);

        // Start setup. This is asynchronous and the specified listener
        // will be called once setup completes.
        Log.d(TAG, "Starting setup.");
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                Log.d(TAG, "Setup finished.");

                if (!result.isSuccess()) {
                    // Oh noes, there was a problem.
                    complain("Problem setting up in-app billing: " + result);
                    return;
                }

                // Have we been disposed of in the meantime? If so, quit.
                if (mHelper == null) return;

                // Important: Dynamically register for broadcast messages about updated purchases.
                // We register the receiver here instead of as a <receiver> in the Manifest
                // because we always call getPurchases() at startup, so therefore we can ignore
                // any broadcasts sent while the app isn't running.
                // Note: registering this listener in an Activity is a bad idea, but is done here
                // because this is a SAMPLE. Regardless, the receiver must be registered after
                // IabHelper is setup, but before first call to getPurchases().
                mBroadcastReceiver = new IabBroadcastReceiver(SettingActivity.this);
                IntentFilter broadcastFilter = new IntentFilter(IabBroadcastReceiver.ACTION);
                registerReceiver(mBroadcastReceiver, broadcastFilter);

                // IAB is fully set up. Now, let's get an inventory of stuff we own.
                Log.d(TAG, "Setup successful. Querying inventory.");
                try {
                    mHelper.queryInventoryAsync(mGotInventoryListener);
                } catch (IabHelper.IabAsyncInProgressException e) {
                    complain("Error querying inventory. Another async operation in progress.");
                }
            }
        });
    }

    void complain(String message) {
        Log.e(TAG, "**** TrivialDrive Error: " + message);
        alert("Error: " + message);
    }

    void alert(String message) {
        android.app.AlertDialog.Builder bld = new android.app.AlertDialog.Builder(this);
        bld.setMessage(message);
        bld.setNeutralButton("OK", null);
        Log.d(TAG, "Showing alert dialog: " + message);
        bld.create().show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.resetSetting:
                resetDialog();
                return true;

        }
        return false;

    }

    private void resetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(SettingActivity.this);
        builder.setTitle("Reset App Settings");
        builder.setMessage("It will reset In-App Settings. Also Recent Songs, Counts and Last Played Playlist will be Deleted!");
        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Main.settings.reset();
                Toast.makeText(SettingActivity.this, "Reset Complete", Toast.LENGTH_SHORT).show();
                recreate();
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void setupCheckBoxes() {
        pauseHeadphoneUnplugged.setChecked(Main.settings.get("pauseHeadphoneUnplugged", true));
        resumeHeadphonePlugged.setChecked(Main.settings.get("resumeHeadphonePlugged", true));
        headphoneControl.setChecked(Main.settings.get("headphoneControl", true));
        saveRecent.setChecked(Main.settings.get("saveRecent", true));
        saveCount.setChecked(Main.settings.get("saveCount", true));
        savePlaylist.setChecked(Main.settings.get("savePlaylist", true));
    }

    // User clicked the "No ads" button.
    public void noAdsClicked(View arg0) {
        if (Main.settings.get("saveNoAds", false)) {
            Toast.makeText(this, "You have purchase for no ads", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Upgrade button clicked; launching purchase flow for upgrade.");
        setWaitScreen(true);

        /* TODO: for security, generate your payload here for verification. See the comments on
         *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
         *        an empty string, but on a production app you should carefully generate this. */
        String payload = "";

        try {
            mHelper.launchPurchaseFlow(this, SKU_TO_BUY, RC_REQUEST,
                    mPurchaseFinishedListener, payload);
        } catch (IabHelper.IabAsyncInProgressException e) {
            complain("Error launching purchase flow. Another async operation in progress.");
            setWaitScreen(false);
        }
    }

    private void setListeners() {

        tvNoAds.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                noAdsClicked(v);
            }
        });

        mode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showModeDialog();
            }
        });

        theme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeTheme();
            }
        });

        pauseHeadphoneUnplugged.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Main.settings.set("pauseHeadphoneUnplugged", true);
                } else {
                    Main.settings.set("pauseHeadphoneUnplugged", false);
                }
            }
        });

        resumeHeadphonePlugged.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {
                    Main.settings.set("resumeHeadphonePlugged", true);
                } else {
                    Main.settings.set("resumeHeadphonePlugged", false);
                }

            }
        });

        headphoneControl.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {
                    Main.settings.set("headphoneControl", true);
                } else {
                    Main.settings.set("headphoneControl", false);
                }

            }
        });

        saveRecent.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {
                    Main.settings.set("saveRecent", true);
                } else {
                    Main.settings.set("saveRecent", false);
                }

            }
        });

        saveCount.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {
                    Main.settings.set("saveCount", true);
                } else {
                    Main.settings.set("saveCount", false);
                }
            }
        });

        savePlaylist.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {
                    Main.settings.set("savePlaylist", true);
                } else {
                    Main.settings.set("savePlaylist", false);
                }

            }
        });

        mBottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                switch (newState) {
                    case BottomSheetBehavior.STATE_HIDDEN:
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED: {
                    }
                    break;
                    case BottomSheetBehavior.STATE_COLLAPSED: {
                        mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    }
                    break;
                    case BottomSheetBehavior.STATE_DRAGGING:
                        break;
                    case BottomSheetBehavior.STATE_SETTLING:
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {

            }
        });
    }

    private void changeTheme() {
        selectedTheme = ThemeUtil.getCurrentActiveTheme();

        openBottomSheet();
        prepareThemeData();
        mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void openBottomSheet() {

        mRecyclerView = findViewById(R.id.recyclerViewBottomSheet);

        mAdapter = new ThemeAdapter(mThemeList);

        RecyclerView.LayoutManager mLayoutManager = new GridLayoutManager(getApplicationContext(), 4);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setAdapter(mAdapter);
    }

    private void prepareThemeData() {
        mThemeList.clear();
        mThemeList.addAll(ThemeUtil.getThemeList());
        mAdapter.notifyDataSetChanged();
    }

    private void showModeDialog() {
        CharSequence[] values = {"Day Mode", "Nigh Mode"};
        AlertDialog.Builder builder = new AlertDialog.Builder(SettingActivity.this);
        builder.setTitle("Set Day/Night Mode");
        int checkeditem = Main.settings.get("modes", "Day").equals("Day") ? 0 : 1;
        int[] newcheckeditem = {checkeditem};
        builder.setSingleChoiceItems(values, checkeditem, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int item) {
                        switch (item) {
                            case 0:
                                newcheckeditem[0] = 0;
                                break;
                            case 1:
                                newcheckeditem[0] = 1;
                                break;

                        }
                    }
                }
        );

        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (checkeditem == newcheckeditem[0]) {
                    dialog.dismiss();
                } else {
                    if (newcheckeditem[0] == 1) {
                        Main.settings.set("modes", "Night");
                    } else {
                        Main.settings.set("modes", "Day");
                    }
                    Toast.makeText(SettingActivity.this, "Changes Made", Toast.LENGTH_SHORT).show();
                    recreate();
                }
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.settingsmenu, menu);
        return true;
    }

    public void sendFeedback(View view) {
        ExtraUtils.sendFeedback(SettingActivity.this);
    }

    public void gotoFAQ(View view) {
        ExtraUtils.openCustomTabs(SettingActivity.this, "https://github.com/iamSahdeep/Bop/blob/master/FAQs.md");
    }

    public void gotoPP(View view) {
        ExtraUtils.openCustomTabs(SettingActivity.this, "https://github.com/iamSahdeep/Bop/blob/master/privacy_policy.md");
    }

    public void gotoGithub(View view) {
        ExtraUtils.openCustomTabs(SettingActivity.this, "https://github.com/iamSahdeep/Bop");
    }

    public void cancelTheme(View view) {
        mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    public void saveTheme(View view) {
        Main.settings.set("themes", getResources().getStringArray(R.array.themes_values)[selectedTheme]);
        Toast.makeText(SettingActivity.this, "Changes Made", Toast.LENGTH_SHORT).show();
        recreate();
    }

    public void AdvancedFragment(View view) {
        findViewById(R.id.scrollSettings).setVisibility(View.GONE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.replaceAdvaced, AdvancedSettings.newInstance("", "")).addToBackStack(null).commit();
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            findViewById(R.id.scrollSettings).setVisibility(View.VISIBLE);
        } else super.onBackPressed();
    }

    @Override
    public void onFragmentInteraction(String what) {
        if (what.equals("jump")) {
            createFWDialog();
        } else if (what.equals("rescan")) {
            rescanMediaStore();
        } else if (what.equals("sleep")) {
            createSTdialog();
        }
    }

    private void createSTdialog() {
        CharSequence[] values = {"5 min", "10 min", "15 min", "20 min", "25 min"};
        AlertDialog.Builder builder = new AlertDialog.Builder(SettingActivity.this);
        builder.setTitle("Set Sleep timer to close music player if paused");
        int checkeditem = Main.settings.get("sleepTimer", 5);
        int[] newcheckeditem = {checkeditem};
        builder.setSingleChoiceItems(values, (checkeditem / 5) - 1, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int item) {
                        newcheckeditem[0] = item;
                    }
                }
        );

        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (checkeditem == newcheckeditem[0]) {
                    dialog.dismiss();
                } else {
                    if (newcheckeditem[0] == 0) {
                        Main.settings.set("sleepTimer", 5);
                    } else if (newcheckeditem[0] == 1) {
                        Main.settings.set("sleepTimer", 10);
                    } else if (newcheckeditem[0] == 2) {
                        Main.settings.set("sleepTimer", 15);
                    } else if (newcheckeditem[0] == 3) {
                        Main.settings.set("sleepTimer", 20);
                    } else if (newcheckeditem[0] == 4) {
                        Main.settings.set("sleepTimer", 25);
                    } else {
                        Main.settings.set("sleepTimer", 5);
                    }
                    Toast.makeText(SettingActivity.this, "Changes Made", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void createFWDialog() {
        CharSequence[] values = {"5 sec", "10 sec", "15 sec", "20 sec", "25 sec"};
        AlertDialog.Builder builder = new AlertDialog.Builder(SettingActivity.this);
        builder.setTitle("Set jump value in forwar/rewind");
        int checkeditem = Main.settings.get("jumpValue", 10);
        int[] newcheckeditem = {checkeditem};
        builder.setSingleChoiceItems(values, (checkeditem / 5) - 1, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int item) {
                        newcheckeditem[0] = item;
                    }
                }
        );

        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (checkeditem == newcheckeditem[0]) {
                    dialog.dismiss();
                } else {
                    if (newcheckeditem[0] == 0) {
                        Main.settings.set("jumpValue", 5);
                    } else if (newcheckeditem[0] == 1) {
                        Main.settings.set("jumpValue", 10);
                    } else if (newcheckeditem[0] == 2) {
                        Main.settings.set("jumpValue", 15);
                    } else if (newcheckeditem[0] == 3) {
                        Main.settings.set("jumpValue", 20);
                    } else if (newcheckeditem[0] == 4) {
                        Main.settings.set("jumpValue", 25);
                    } else {
                        Main.settings.set("jumpValue", 10);
                    }

                    Toast.makeText(SettingActivity.this, "Changes Made", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void rescanMediaStore() {
        ProgressDialog lol = new ProgressDialog(SettingActivity.this);
        lol.setMessage("Sending BroadCast to Scan");
        lol.setCancelable(false);
        lol.show();
        MediaScannerConnection.scanFile(
                getApplicationContext(),
                new String[]{"file://" + Environment.getExternalStorageDirectory()},
                new String[]{"audio/mp3", "audio/*"},
                new MediaScannerConnection.MediaScannerConnectionClient() {
                    public void onMediaScannerConnected() {

                    }

                    public void onScanCompleted(String path, Uri uri) {
                        lol.cancel();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(SettingActivity.this, "Remove from recents and restart application", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });

    }

    // Callback for when a purchase is finished
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);

            // if we were disposed of in the meantime, quit.
            if (mHelper == null) return;

            if (result.isFailure()) {
                complain("Error purchasing: " + result);
                setWaitScreen(false);
                return;
            }
            if (!verifyDeveloperPayload(purchase)) {
                complain("Error purchasing. Authenticity verification failed.");
                setWaitScreen(false);
                return;
            }

            Log.d(TAG, "Purchase successful.");

            if (purchase.getSku().equals(SKU_TO_BUY)) {
                // bought the premium upgrade!
                Log.d(TAG, "Purchase is premium upgrade. Congratulating user.");
                alert("Thank you for upgrading to premium!");
                mIsNoAds = true;
                Main.settings.set("saveNoAds", true);
                updateUi();
                setWaitScreen(false);
            }
        }
    };

    // Enables or disables the "please wait" screen.
    void setWaitScreen(boolean set) {
        findViewById(R.id.main_purchase).setVisibility(set ? View.GONE : View.VISIBLE);
        findViewById(R.id.wait_purchase).setVisibility(set ? View.VISIBLE : View.GONE);
    }

    // Listener that's called when we finish querying the items and subscriptions we own
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.d(TAG, "Query inventory finished.");

            // Have we been disposed of in the meantime? If so, quit.
            if (mHelper == null) return;

            // Is it a failure?
            if (result.isFailure()) {
                complain("Failed to query inventory: " + result);
                return;
            }

            Log.d(TAG, "Query inventory was successful.");

            /*
             * Check for items we own. Notice that for each purchase, we check
             * the developer payload to see if it's correct! See
             * verifyDeveloperPayload().
             */

            // Do we have the premium upgrade?
            Purchase noAdsPurchase = inventory.getPurchase(SKU_TO_BUY);
            mIsNoAds = (noAdsPurchase != null && verifyDeveloperPayload(noAdsPurchase));
            Log.d(TAG, "User is " + (mIsNoAds ? "PURCHASED NO ADS" : "NOT YET PURCHASE"));
            Main.settings.set("saveNoAds", mIsNoAds);

            updateUi();
            Log.d(TAG, "Initial inventory query finished; enabling main UI.");
        }
    };

    private void updateUi() {
        tvNoAds.setEnabled(!mIsNoAds);
        cbNoAds.setChecked(mIsNoAds);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
        if (mHelper == null) return;

        // Pass on the activity result to the helper for handling
        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        } else {
            Log.d(TAG, "onActivityResult handled by IABUtil.");
        }
    }

    /**
     * Verifies the developer payload of a purchase.
     */
    boolean verifyDeveloperPayload(Purchase p) {
        String payload = p.getDeveloperPayload();

        /*
         * TODO: verify that the developer payload of the purchase is correct. It will be
         * the same one that you sent when initiating the purchase.
         *
         * WARNING: Locally generating a random string when starting a purchase and
         * verifying it here might seem like a good approach, but this will fail in the
         * case where the user purchases an item on one device and then uses your app on
         * a different device, because on the other device you will not have access to the
         * random string you originally generated.
         *
         * So a good developer payload has these characteristics:
         *
         * 1. If two different users purchase an item, the payload is different between them,
         *    so that one user's purchase can't be replayed to another user.
         *
         * 2. The payload must be such that you can verify it even when the app wasn't the
         *    one who initiated the purchase flow (so that items purchased by the user on
         *    one device work on other devices owned by the user).
         *
         * Using your own server to store and verify developer payloads across app
         * installations is recommended.
         */

        return true;
    }

    @Override
    public void receivedBroadcast() {
        // Received a broadcast notification that the inventory of items has changed
        Log.d(TAG, "Received broadcast notification. Querying inventory.");
        try {
            mHelper.queryInventoryAsync(mGotInventoryListener);
        } catch (IabHelper.IabAsyncInProgressException e) {
            complain("Error querying inventory. Another async operation in progress.");
        }
    }
}
