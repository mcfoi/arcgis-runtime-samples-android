package it.esri.android.facilitysurvey;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.DrawerLayout;
import android.widget.Toast;

import com.esri.android.map.FeatureLayer;
import com.esri.android.map.Layer;
import com.esri.android.map.MapView;
import com.esri.core.ags.FeatureServiceInfo;
import com.esri.core.geodatabase.Geodatabase;
import com.esri.core.geodatabase.GeodatabaseFeatureTable;
import com.esri.core.geometry.Envelope;
import com.esri.core.io.UserCredentials;
import com.esri.core.map.CallbackListener;
import com.esri.core.tasks.geodatabase.GenerateGeodatabaseParameters;
import com.esri.core.tasks.geodatabase.GeodatabaseStatusCallback;
import com.esri.core.tasks.geodatabase.GeodatabaseStatusInfo;
import com.esri.core.tasks.geodatabase.GeodatabaseSyncTask;

import java.io.File;
import java.io.FileNotFoundException;


public class MainActivity extends ActionBarActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks, Oauth2Fragment.OnFragmentInteractionListener, Oauth2Fragment.OnUserCredentialsRetrieved {

    protected static final String LOG_TAG = "FacilitySurvey";
    private static final String TAG_MAP_FRAGMENT = "MapFragment";
    private static final String TAG_OAUTH2_FRAGMENT = "OAuth2Fragment";
    public static final String KEY_USER_CREDENTIALS_PWD = "KEY_USER_CREDENTIALS_PWD";
    public static final String KEY_USER_CREDENTIALS_USR = "KEY_USER_CREDENTIALS_USR";
    public static final String KEY_USER_CREDENTIALS_TKN = "KEY_USER_CREDENTIALS_TKN";

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private Context context;
    private NavigationDrawerFragment mNavigationDrawerFragment;
    private MapFragment mMapFragment;
    private Oauth2Fragment mOAuthFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;
    private static File externalStorageDir;
    private static String offlineDataSDCardDirName;
    private String fServiceUrl;
    protected static String OFFLINE_FILE_EXTENSION = ".geodatabase";
    private ProgressDialog mProgressDialog;
    static GeodatabaseSyncTask gdbSyncTask;
    private UserCredentials mCurrentUserLoginCredentials;

    /**
     * Returns an instance of UserCredential from savedInstanceState Bundle
     * @param savedInstanceState
     * @return
     */
    private UserCredentials getUserCredentialsFromSavedInstanceState(Bundle savedInstanceState) {
        UserCredentials uc = new UserCredentials();
        String pwd = savedInstanceState.getString(KEY_USER_CREDENTIALS_PWD, "");
        String usr = savedInstanceState.getString(KEY_USER_CREDENTIALS_USR, "");
        String tkn = savedInstanceState.getString(KEY_USER_CREDENTIALS_TKN, "");
        if (pwd != "" && usr != ""){
            uc.setUserAccount(usr, pwd);
            return uc;
        } else if (tkn != ""){
            // TODO : check if the referer is the Client_ID or something else!!
            uc.setUserToken(tkn, getResources().getString(R.string.client_id));
            return uc;
        } else {
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if( savedInstanceState != null ) {
            mCurrentUserLoginCredentials = getUserCredentialsFromSavedInstanceState(savedInstanceState);
        }

        context = this;

        FragmentManager fragMgr = getSupportFragmentManager();

        // Find existing fragments (if any)
        mNavigationDrawerFragment = (NavigationDrawerFragment) getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mMapFragment = (MapFragment) fragMgr.findFragmentByTag(TAG_MAP_FRAGMENT);
        mOAuthFragment = (Oauth2Fragment) fragMgr.findFragmentByTag(TAG_OAUTH2_FRAGMENT);

        if (mMapFragment == null) {
            // There's no existing map fragment, so create one
            createMapFragment(MapFragment.BASEMAP_NAME_STREETS);
        } else {
            // There's an existing map fragment - need to remove it from main_fragment_container before we can add it to
            // map_fragment_container_twopane
            fragMgr.beginTransaction().remove(mMapFragment).commit();
            fragMgr.executePendingTransactions();
        }

        // Display map fragment in container
        fragMgr.beginTransaction().add(R.id.container, mMapFragment, TAG_MAP_FRAGMENT).commit();

        if (mOAuthFragment == null) {
            mOAuthFragment = createOAuth2Fragment();
        } else {
            showOAuth2Fragment();
        }
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

        // get sdcard resource names
        externalStorageDir = Environment.getExternalStorageDirectory();
        offlineDataSDCardDirName = this.getResources().getString(R.string.sdcard_offline_dir);
        // create service layer
        fServiceUrl = this.getResources().getString(R.string.featureservice_url);

        mProgressDialog = new SelfCleaningProgressDialog(context);
        mProgressDialog.setTitle("Create local runtime geodatabase");
    }

    class SelfCleaningProgressDialog extends ProgressDialog {

        private OnDismissListener customListener;
        private SelfCleaningProgressDialog myDialog;

        public SelfCleaningProgressDialog(Context context) {
            super(context);
            myDialog = this;
            customListener = new OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    myDialog.setMessage("");
                }
            };

            setOnDismissListener(customListener);
        }
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {

        // update the main content by replacing fragments
        /*
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.container, PlaceholderFragment.newInstance(position + 1))
                .commit();
        */
        if (mNavigationDrawerFragment != null) {
            String mapName = mNavigationDrawerFragment.getCurrentMapName();
            mMapFragment.changeBasemap(mapName);
        }
    }

    /**
     * Creates a new map fragment.
     *
     * @param id String identifier of basemap to display.
     */
    private void createMapFragment(String id) {
        Bundle arguments = new Bundle();
        arguments.putString(MapFragment.ARG_BASEMAP_ID, id);
        mMapFragment = new MapFragment();
        mMapFragment.setArguments(arguments);
    }

    /**
     * Create an OAuth2 fragment
     */
    private Oauth2Fragment createOAuth2Fragment(){
        Bundle arguments = new Bundle();
        //arguments.putString(MapFragment.ARG_BASEMAP_ID, id);
        Oauth2Fragment fragment = new Oauth2Fragment();
        fragment.setArguments(arguments);
        return fragment;
    }


    /**
     * Sets for UserCredentials and closes OAuth2Fragment returning to previous fragment on Stack
     *
     * @param credentials
     */
    public void setCredentialsAndPopBack(UserCredentials credentials) {
        mCurrentUserLoginCredentials = credentials;
        FragmentManager fM = getSupportFragmentManager();
        fM.popBackStack();
    }

    /**
     * Returns current user credentials
     *
     * @return UserCredentials
     */
    public UserCredentials getCredentials() {
        return mCurrentUserLoginCredentials;
    }

    /*
	 * Create the geodatabase file location and name structure
	 */
    static String createGeodatabaseFilePath(String filename) {
        StringBuilder sb = new StringBuilder();
        sb.append(externalStorageDir.getAbsolutePath());
        sb.append(File.separator);
        sb.append(offlineDataSDCardDirName);
        sb.append(File.separator);
        sb.append(filename);
        sb.append(OFFLINE_FILE_EXTENSION);
        return sb.toString();
    }

    public void onSectionAttached(int number) {
        switch (number) {
            case 1:
                mTitle = getString(R.string.title_section1);
                break;
            case 2:
                mTitle = getString(R.string.title_section2);
                break;
            case 3:
                mTitle = getString(R.string.title_section3);
                break;
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }


    @Override
    public void onSaveInstanceState(Bundle outState){
        if (mCurrentUserLoginCredentials != null) {
            String pwd = mCurrentUserLoginCredentials.getPassword();
            String usr = mCurrentUserLoginCredentials.getUserName();
            String tkn = mCurrentUserLoginCredentials.getToken();

            if (pwd != null) {
                outState.putString(KEY_USER_CREDENTIALS_PWD, pwd);
                outState.putString(KEY_USER_CREDENTIALS_USR, usr);
            } else {
                outState.putString(KEY_USER_CREDENTIALS_TKN, usr);
            }
        }
        super.onSaveInstanceState(outState);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        boolean retVal = false;
        switch (item.getItemId()) {
            case R.id.action_download:
                downloadData(fServiceUrl);
                retVal = true;
                break;
            case R.id.action_settings:
                showOAuth2Fragment();
                retVal = true;
                break;
        }
        return retVal;
    }

    /**
     * Add OAuth2Fragment to MainActivity
     */
    private void showOAuth2Fragment() {

        FragmentManager fragMgr = getSupportFragmentManager();
        FragmentTransaction transaction = fragMgr.beginTransaction();
        // Replace whatever is in the container view with this fragment,
        // and add the transaction to the back stack so the user can navigate back
        transaction.replace(R.id.container, mOAuthFragment, TAG_OAUTH2_FRAGMENT);
        transaction.addToBackStack(null);
        // Commit the transaction
        transaction.commit();
    }

    /**
     * Create the GeodatabaseTask from the feature service URL w/o credentials.
     */
    private void downloadData(String url) {
        Log.i(LOG_TAG, "Create GeoDatabase");
        // create a dialog to update user on progress
        mProgressDialog.show();
        // create the GeodatabaseTask

        gdbSyncTask = new GeodatabaseSyncTask(url, mCurrentUserLoginCredentials);
        gdbSyncTask.fetchFeatureServiceInfo(new CallbackListener<FeatureServiceInfo>() {

            @Override
            public void onError(Throwable arg0) {
                Log.e(LOG_TAG, "Error fetching FeatureServiceInfo");
                if (arg0 instanceof com.esri.core.io.EsriSecurityException) {
                    if (mCurrentUserLoginCredentials == null) {
                        setProgressDialogMessage((MainActivity) context, "The requested FeatureService requires authentication.\nPlease, proceed to Login.");
                    } else {
                        setProgressDialogMessage((MainActivity) context, "The current User cannot access the requested FeatureService.\nPlease, login with a different user.");
                    }
                } else {
                    setProgressDialogMessage((MainActivity) context, "An generic error occurred: could not perform download");
                }
            }

            @Override
            public void onCallback(FeatureServiceInfo fsInfo) {
                if (fsInfo.isSyncEnabled()) {
                    createGeodatabase(fsInfo);
                }
            }
        });

    }

    /**
     * Set up parameters to pass the the submitTask() method. A
     * {@link CallbackListener} is used for the response.
     */
    private void createGeodatabase(FeatureServiceInfo featureServerInfo) {
        // set up the parameters to generate a geodatabase
        MapView mMapView = mMapFragment.getMapView();
        GenerateGeodatabaseParameters params = new GenerateGeodatabaseParameters(
                featureServerInfo, mMapView.getExtent(),
                mMapView.getSpatialReference());

        // a callback which fires when the task has completed or failed.
        CallbackListener<String> gdbResponseCallback = new CallbackListener<String>() {
            @Override
            public void onError(final Throwable e) {
                Log.e(LOG_TAG, "Error creating geodatabase");
                setProgressDialogMessage((MainActivity) context, "");
                mProgressDialog.dismiss();
            }

            @Override
            public void onCallback(String path) {
                Log.i(LOG_TAG, "Geodatabase is: " + path);
                setProgressDialogMessage((MainActivity) context, "");
                mProgressDialog.dismiss();
                // update map with local feature layer from geodatabase
                updateFeatureLayer(path);
                // log the path to the data on device
                Log.i(LOG_TAG, "path to geodatabase: " + path);
            }
        };

        // a callback which updates when the status of the task changes
        GeodatabaseStatusCallback statusCallback = new GeodatabaseStatusCallback() {
            @Override
            public void statusUpdated(final GeodatabaseStatusInfo status) {
                // get current status
                String progress = status.getStatus().toString();
                // update progress bar on main thread
                setProgressDialogMessage((MainActivity) context, progress);

            }
        };

        // create the fully qualified path for geodatabase file
        String geoDbFilename = fromServiceURLtoServiceNameNoSpaces(fServiceUrl);
        String localGdbFilePath = createGeodatabaseFilePath(geoDbFilename);

        int last = localGdbFilePath.lastIndexOf("/");
        String path = localGdbFilePath.substring(0, last);
        File storagePathFile = new File(path);
        // submit task
        boolean canWrite = true;
        if (!storagePathFile.isDirectory()){
            canWrite = false;
            canWrite = storagePathFile.mkdirs();
        }
        if (canWrite) {
            File localGdbFile = new File(localGdbFilePath);
            boolean couldDelete = true;
            if (localGdbFile.exists()) {
                couldDelete = false;
                couldDelete = localGdbFile.delete();
            }
            if (couldDelete) {
                // get geodatabase based on params
                submitTask(params, localGdbFilePath, statusCallback,
                        gdbResponseCallback);
            } else {
                setProgressDialogMessage(this, "Cannot delete preexisting file!\nNo database downloaded.");
            }
        } else {
            setProgressDialogMessage(this, "Cannot write to local folder!\nNo database downloaded.");
        }
    }



    /**
     * Request database, poll server to get status, and download the file
     */
    private static void submitTask(GenerateGeodatabaseParameters params,
                                   String file,
                                   GeodatabaseStatusCallback statusCallback,
                                   CallbackListener<String> gdbResponseCallback) {

        gdbSyncTask.generateGeodatabase(params, file, false, statusCallback,
                gdbResponseCallback);
    }

    /**
     * Returns from a Service URL, in the form of
     * http://services.arcgis.com/lY0By1TVrNVXFf6Y/arcgis/rest/services/Livello_modificabile_WGS84/FeatureServer
     * the name of the service without any whitespace.
     * @param url
     * @return
     */
    private String fromServiceURLtoServiceNameNoSpaces(String url) {
        String[] worlds = url.split("/");
        String serviceName = worlds[(worlds.length)-2];
        serviceName = serviceName.replace(" ","_");
        return serviceName;
    }


    private void setProgressDialogMessage(final MainActivity activity, final String message) {
        activity.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mProgressDialog.setMessage(message);
            }

        });
    }


    /**
     * Add feature layer from local geodatabase to map
     *
     * @param featureLayerPath
     */
    private void updateFeatureLayer(String featureLayerPath) {
        // create a new geodatabase
        Geodatabase localGdb = null;
        try {
            localGdb = new Geodatabase(featureLayerPath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // Geodatabase contains GdbFeatureTables representing attribute data
        // and/or spatial data. If GdbFeatureTable has geometry add it to
        // the MapView as a Feature Layer
        if (localGdb != null) {
            Envelope dataExtent = new Envelope();
            removeAllFeatureLayers();
            for (GeodatabaseFeatureTable gdbFeatureTable : localGdb
                    .getGeodatabaseTables()) {
                if (gdbFeatureTable.hasGeometry()){
                    mMapFragment.getMapView().addLayer(new FeatureLayer(gdbFeatureTable));
                    Envelope layerEnv = gdbFeatureTable.getExtent();
                    dataExtent.merge(layerEnv);
                }
            }
            if (!dataExtent.isEmpty()) {
                mMapFragment.getMapView().zoomTo(dataExtent.getCenter(), 1.0f);
            }
        }
    }

    /**
     * Removes all FeatureLayer from current MapView
     */
    private void removeAllFeatureLayers() {

        Layer[] layers = mMapFragment.getMapView().getLayers();
        for (Layer l : layers) {
            if (l instanceof com.esri.android.map.FeatureLayer){
                mMapFragment.getMapView().removeLayer(l);
            }
        }
    }

    @Override
    public void onUserCredentialsRetrieved(UserCredentials credentials) {

        setCredentialsAndPopBack(credentials);

    }

    @Override
    public void onFragmentInteraction(Uri uri) {

        Toast.makeText(this, "An interaction with a Fragment occurred", Toast.LENGTH_SHORT).show();

    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((MainActivity) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }

}