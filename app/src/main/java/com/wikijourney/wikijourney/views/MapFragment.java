package com.wikijourney.wikijourney.views;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.wikijourney.wikijourney.R;
import com.wikijourney.wikijourney.functions.CustomInfoWindow;
import com.wikijourney.wikijourney.functions.Map;
import com.wikijourney.wikijourney.functions.POI;
import com.wikijourney.wikijourney.functions.UI;

import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.overlays.Marker;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.ArrayList;

import cz.msebera.android.httpclient.Header;

public class MapFragment extends Fragment {

    // Variables for API
    private static final String API_URL = "http://wikijourney.eu/api/api.php?";
    private String language = "fr";
    private double paramRange;
    private int paramMaxPoi;
    private String paramPlace;
    private int paramMethod; //Could be around or place, depends on which button was clicked.

    //Now the variables we are going to use for the rest of the program.
    private LocationManager locationManager;
    private LocationListener locationListener;

    private Snackbar locatingSnackbar;
    private Snackbar downloadSnackbar;


    public MapFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        // These lines initialize the map settings
        final MapView map = (MapView) view.findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);
        IMapController mapController = map.getController();
        mapController.setZoom(16);

        // We get the Bundle values
        Bundle args = getArguments();

        try {
            paramMaxPoi = args.getInt(HomeFragment.EXTRA_OPTIONS[0]);
        } catch (Exception e) {
            paramMaxPoi = getResources().getInteger(R.integer.default_maxPOI);
        }
        try {
            paramRange = args.getDouble(HomeFragment.EXTRA_OPTIONS[1]);
        } catch (Exception e) {
            paramRange = getResources().getInteger(R.integer.default_range);
        }
        try {
            paramPlace = args.getString(HomeFragment.EXTRA_OPTIONS[2]);
        } catch (Exception e) {
            paramPlace = "null"; // Place value
        }
        try {
            paramMethod = args.getInt(HomeFragment.EXTRA_OPTIONS[3]);
        } catch (Exception e) { // https://stackoverflow.com/questions/9702216/get-the-latest-fragment-in-backstack
            // I totally forgot what this does, maybe so we don't refresh the Map if the user already has geolocated himself...
            // Or if the user chose Around Me method or Around A Place method... Oops
            int previousFragmentId = getActivity().getFragmentManager().getBackStackEntryCount()-1;
            FragmentManager.BackStackEntry backEntry = getActivity().getFragmentManager().getBackStackEntryAt(previousFragmentId);
            if (backEntry.getName().equals("MapFragmentFindingPoi")) {
                paramMethod = HomeFragment.METHOD_AROUND;
            } else {
                paramMethod = -1;
            }
        }

        if (paramMethod == HomeFragment.METHOD_AROUND) {
            // Display a Snackbar while the phone locates the user, so he doesn't think the app crashed
            locatingSnackbar = Snackbar.make(getActivity().findViewById(R.id.fragment_container), R.string.snackbar_locating, Snackbar.LENGTH_INDEFINITE);
            locatingSnackbar.show();

        /* ====================== GETTING LOCATION ============================ */

            // Acquire a reference to the system Location Manager
            locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
//        Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            // Define a listener that responds to location updates
            locationListener = new LocationListener() {
                public void onLocationChanged(Location location) {
                    // Once located, download the info from the API and display the map
                    locatingSnackbar.dismiss();
                    drawMap(location, map, locationManager, this);
                }

                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                public void onProviderEnabled(String provider) {
                }

                public void onProviderDisabled(String provider) {
                }
            };

            // Register the listener with the Location Manager to receive location updates
//        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        } else if(paramMethod == HomeFragment.METHOD_PLACE) {
            // TODO
//            drawMap(paramPlace, map);
        }

/* ====================== END GETTING LOCATION ============================ */

        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // Stop the Geolocation if the user leaves the MapFragment early
        locationManager.removeUpdates(locationListener);
        if (locatingSnackbar != null) {
            locatingSnackbar.dismiss();
        }
        if (downloadSnackbar != null) {
            downloadSnackbar.dismiss();
        }
    }



    private void drawMap(Location location, MapView map, LocationManager locationManager, LocationListener locationListener) {
        // TODO Temporary fix
        // This stop the location updates, so the map doesn't always refresh
        locationManager.removeUpdates(locationListener);

        IMapController mapController = map.getController();

        // This starts the map at the desired point
        final GeoPoint startPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        mapController.setCenter(startPoint);

        // Now we add a marker using osmBonusPack
        Marker startMarker = new Marker(map);
        startMarker.setPosition(startPoint);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(startMarker);

        // And we have to use this to refresh the map
        map.invalidate();

        // We can change some properties of the marker (don't forget to refresh the map !!)
        startMarker.setInfoWindow(new CustomInfoWindow(map));
        Drawable icon = ContextCompat.getDrawable(getActivity(), R.drawable.ic_place);
        startMarker.setIcon(icon);
        startMarker.setTitle(getString(R.string.you_are_here));
        map.invalidate();


        // We get the POI around the user with WikiJourney API
        String url;
        url = API_URL + "long=" + startPoint.getLongitude() + "&lat=" + startPoint.getLatitude()
                + "&maxPOI=" + paramMaxPoi + "&range=" + paramRange + "&lg=" + language;

        // Check if the Internet is up
        final ConnectivityManager connMgr = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        // These are needed for the download library and the methods called later
        final Context context = this.getActivity();
        final MapFragment mapFragment = this;

        if (networkInfo != null && networkInfo.isConnected()) {
            // Show a Snackbar while we wait for WikiJourney server, so the user doesn't think the app crashed
            downloadSnackbar = Snackbar.make(getView(), R.string.snackbar_downloading, Snackbar.LENGTH_INDEFINITE);
            downloadSnackbar.show();
            // Download from the WJ API
            AsyncHttpClient client = new AsyncHttpClient();
            client.setTimeout(30_000); // Set timeout to 30s, the server may be slow...
            client.get(context, url, new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                    downloadSnackbar.dismiss();
                    ArrayList<POI> poiArrayList;
                    String errorOccurred = "true";
                    String errorMessage = null;
                    // We check if the download worked
                    try {
                        errorOccurred = response.getJSONObject("err_check").getString("value");
                        if (errorOccurred.equals("true")) {
                            errorMessage = response.getJSONObject("err_check").getString("err_msg");
                        }
                        else {
                            errorOccurred = "false";
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (errorOccurred.equals("true")) {
                        UI.openPopUp(mapFragment.getActivity(), mapFragment.getResources().getString(R.string.error_download_api_response_title), errorMessage);
                    } else {
                        poiArrayList = POI.parseApiJson(response, context);
                        Map.drawPOI(mapFragment, poiArrayList);
                    }
                }

                @Override
                public void onProgress(long bytesWritten, long totalSize) {
                    Log.d("progress", "Downloading " + bytesWritten + " of " + totalSize);
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                    try {
                        Log.e("Error", errorResponse.toString());
                    } catch (Exception e) {
                        Log.e("Error", "Error while downloading the API response");
                    }
                    finally {
                        UI.openPopUp(mapFragment.getActivity(), getResources().getString(R.string.error_download_api_response_title), getResources().getString(R.string.error_download_api_response));
                    }
                }

                @Override
                public void onRetry(int retryNo) {
                    Log.e("Error", "Retrying for the " + retryNo + " time");
                    super.onRetry(retryNo);
                }
            });
        } else {
            UI.openPopUp(mapFragment.getActivity(), getResources().getString(R.string.error_activate_internet_title), getResources().getString(R.string.error_activate_internet));
        }
    }
    /*public void drawMap(String place, MapView map) {
        IMapController mapController = map.getController();

        // This starts the map at the desired point
        final GeoPoint startPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        mapController.setCenter(startPoint);

        // Now we add a marker using osmBonusPack
        Marker startMarker = new Marker(map);
        startMarker.setPosition(startPoint);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(startMarker);

        // And we have to use this to refresh the map
        map.invalidate();

        // We can change some properties of the marker (don't forget to refresh the map !!)
        startMarker.setInfoWindow(new CustomInfoWindow(map));
        Drawable icon = ContextCompat.getDrawable(getActivity(), R.drawable.ic_place);
        startMarker.setIcon(icon);
        startMarker.setTitle(getString(R.string.you_are_here));
        map.invalidate();


        // We get the POI around the user with WikiJourney API
        String url;
        url = API_URL + "long=" + startPoint.getLongitude() + "&lat=" + startPoint.getLatitude()
                + "&maxPOI=" + paramMaxPoi + "&lg=" + language;

        ConnectivityManager connMgr = (ConnectivityManager)
                getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            new DownloadApi(this).execute(url, place);
        } else {
            UI.openPopUp(new HomeFragment(), getResources().getString(R.string.error_activate_internet_title), getResources().getString(R.string.error_activate_internet));
        }
    }*/
}