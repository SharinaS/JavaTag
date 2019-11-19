package com.javaawesome.tag;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.amazonaws.amplify.generated.graphql.GetSessionQuery;
import com.amazonaws.mobile.config.AWSConfiguration;
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient;
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers;
import com.apollographql.apollo.GraphQLCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nonnull;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener, GoogleMap.OnMyLocationClickListener {

    private GoogleMap mMap;
    AWSAppSyncClient awsAppSyncClient;
    GetSessionQuery.GetSession currentSession;

    LatLng startingPoint;
    final static long REFRESHRATE = 3*1000;
    final static int SUBJECT = 0;
    Handler locationHandler;
    LocationCallback mLocationCallback;
    private FusedLocationProviderClient mFusedLocationClient;
    final private int tagDistance = 50;
    List<Player> itPlayers;
    int itColor = Color.RED;
    int notItColor = Color.GREEN;
    float itHue = BitmapDescriptorFactory.HUE_RED;
    float notItHue = BitmapDescriptorFactory.HUE_GREEN;
    List<Player> players;
    private final String TAG = "javatag";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // asks users for permissions
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);

        // initialize connection with google location services
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // establish connection to AWS
        awsAppSyncClient = AWSAppSyncClient.builder()
                .context(getApplicationContext())
                .awsConfiguration(new AWSConfiguration(getApplicationContext()))
                .build();

        // get the session that user selected from mainactivity
        String sessionId = getIntent().getStringExtra("sessionId");
        Log.i(TAG, "Session ID for map is: " + sessionId);
        queryForSelectedSession(sessionId);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                updateMarkerAndCircleForAllPlayers(players);
            }
        };

//        mFusedLocationClient.requestLocationUpdates(getLocationRequest(), mLocationCallback, null);
    }

    private void sendUserLocationQuery() {

    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTrackingLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setOnMyLocationButtonClickListener(this);
        mMap.setOnMyLocationClickListener(this);

        //TODO: Still need to send the player's location to DB on a timer for updates
        startLocationUpdates();
    }

    @Override
    public void onMyLocationClick(@NonNull Location location) {
        Toast.makeText(this, "Current Location:\n" + location, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onMyLocationButtonClick() {
        Toast.makeText(this, "My Location button clicked", Toast.LENGTH_SHORT).show();
        return false;
    }

    // Called in startLocationUpdates to pull location updates from the DB
    private LocationRequest getLocationRequest() {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        return locationRequest;
    }

    public void stopTrackingLocation() {
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }

    // Starts pulling location updates from the DB
    private void startLocationUpdates() {
        mFusedLocationClient.requestLocationUpdates(getLocationRequest(), mLocationCallback, null);
        mMap.setMyLocationEnabled(true);
    }

    // Creates markers and circles for each player in the list for that session
    private void initializeMarkersAndCirclesForPlayers(List<Player> players) {
        for(Player player: players) {
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(player.getLastLocation())
                    .title(player.getUsername()));
            Circle circle = mMap.addCircle(new CircleOptions()
                    .center(player.getLastLocation())
                    .radius(tagDistance)
                    .fillColor(Color.TRANSPARENT)
                    .strokeWidth(3));

            // change color of marker depending on if player is it or not
            if (player.isIt()) {
                marker.setIcon(BitmapDescriptorFactory.defaultMarker(itHue));
                circle.setStrokeColor(itColor);
            } else {
                marker.setIcon(BitmapDescriptorFactory.defaultMarker(notItHue));
                circle.setStrokeColor(notItColor);
            }

            player.setCircle(circle);
            player.setMarker(marker);

            //      Add a marker in center of game camera and move the camera
            mMap.moveCamera(CameraUpdateFactory.zoomTo(16));
            mMap.moveCamera(CameraUpdateFactory.newLatLng(startingPoint));
            Circle gameBounds = mMap.addCircle(new CircleOptions()
                    .center(startingPoint)
                    .radius(currentSession.radius())
                    .strokeColor(Color.BLUE)
                    .fillColor(Color.TRANSPARENT)
                    .strokeWidth(5));
        }
    }


    private void updateMarkerAndCircleForAllPlayers(List<Player> players) {
        List<Player> playersJustGotTagged = new LinkedList<>();
        for (Player player : players) {
            player.getMarker().setPosition(player.getLastLocation());
            player.getCircle().setCenter(player.getLastLocation());
            if (checkForTag(player)) {
                playersJustGotTagged.add(player);
                player.getMarker().setIcon(BitmapDescriptorFactory.defaultMarker(itHue));
                player.getCircle().setStrokeColor(itColor);
            }

            //TODO: add notifications based on tag changes
//            if (player.isIt()) {
//                player.getMarker().setIcon(BitmapDescriptorFactory.defaultMarker(itHue));
//                player.getCircle().setStrokeColor(itColor);
//            } else {
//                player.getMarker().setIcon(BitmapDescriptorFactory.defaultMarker(notItHue));
//                player.getCircle().setStrokeColor(notItColor);
//            }
        }

        itPlayers.addAll(playersJustGotTagged);
    }

    // Equation is from https://stackoverflow.com/questions/639695/how-to-convert-latitude-or-longitude-to-meters
    // convert to two location points to distance between them in meters
    private double distanceBetweenLatLongPoints(double lat1, double long1, double lat2, double long2) {
        // radius of the Earth in km
        double R = 6378.137;
        double dLat = (lat2 * Math.PI / 180) - (lat1 * Math.PI / 180);
        double dLong = (long2 * Math.PI / 180) - (long1 * Math.PI / 180);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
                        Math.sin(dLong / 2) * Math.sin(dLong / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double d = R * c;
        return d * 1000;
    }

    // check if the player is tagged by the it player
    // check if the distance between the it player and the other player is less than the specified tag distance
    private boolean isTagged(Player player, Player itPlayer) {
        double distanceBetweenPlayers = distanceBetweenLatLongPoints(itPlayer.getLastLocation().latitude,
                itPlayer.getLastLocation().longitude,
                player.getLastLocation().latitude,
                player.getLastLocation().longitude);

        Log.i(TAG, "distance between players is " + distanceBetweenPlayers + " meters");

        if (distanceBetweenPlayers < tagDistance) {
            player.setIt(true);
            itPlayer.setIt(false);
            itPlayer = player;
            return true;
        } else {
            return false;
        }
    }

    private boolean checkForTag(Player player) {
        if (player.isIt()) {
            return false;
        }
        for(Player itPlayer : itPlayers) {
            if (isTagged(player, itPlayer)) {
                //TODO: Add notifications here
                Toast.makeText(this, "" + player.getUsername() + " is now it!!!", Toast.LENGTH_SHORT);
                return true;
            }
        }
        return false;
    }

    // query for the session associated with the sessionId that was passed from MainActivity
    private void queryForSelectedSession(String sessionId) {
        GetSessionQuery getSessionQuery =GetSessionQuery.builder().id(sessionId).build();
        awsAppSyncClient.query(getSessionQuery)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(getSessionCallBack);
    }

    // Callback to get current game session
    private GraphQLCall.Callback<GetSessionQuery.Data> getSessionCallBack = new GraphQLCall.Callback<GetSessionQuery.Data>() {
        @Override
        public void onResponse(@Nonnull final Response<GetSessionQuery.Data> response) {
            currentSession = response.data().getSession();

            //converting from GetSessionItems to players
            players = playerConverter(currentSession.players().items());
            Handler h = new Handler(Looper.getMainLooper()){
                @Override
                public void handleMessage(Message inputMessage){
                    //lat and long for the session
                    startingPoint = new LatLng(currentSession.lat(), currentSession.lon());
                    initializeMarkersAndCirclesForPlayers(players);

                }
            };
            h.obtainMessage().sendToTarget();
        }

        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e(TAG, "error from getSessionQuery: " + e.getMessage());
        }
    };

    private List<Player> playerConverter(List<GetSessionQuery.Item> incomingList){
        List<Player> outGoingList = new LinkedList<>();
        for(GetSessionQuery.Item item : incomingList){
            Player newPlayer = new Player(item);
            outGoingList.add(newPlayer);
        }
        return outGoingList;
    };

    // TODO: Build onDestroy that deletes user data from DB

}
