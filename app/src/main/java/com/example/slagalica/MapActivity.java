package com.example.slagalica;

import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.security.ProviderInstaller;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FirebaseFirestore db;
    private List<Marker> otherMarkers = new ArrayList<>();
    private boolean othersVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        ProviderInstaller.installIfNeededAsync(this, new ProviderInstaller.ProviderInstallListener() {
            @Override
            public void onProviderInstalled() {}
            @Override
            public void onProviderInstallFailed(int errorCode, android.content.Intent recoveryIntent) {}
        });

        db = FirebaseFirestore.getInstance();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        MaterialButton btnToggle = findViewById(R.id.btnToggleOthers);
        btnToggle.setOnClickListener(v -> {
            othersVisible = !othersVisible;
            for (Marker marker : otherMarkers) {
                marker.setVisible(othersVisible);
            }
            btnToggle.setText(othersVisible ? "SAKRIJ OSTALE" : "PRIKAŽI OSTALE");
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        LatLng serbia = new LatLng(44.0165, 21.0059);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(serbia, 7));

        String currentUid = FirebaseAuth.getInstance().getUid();

        db.collection("users").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                for (QueryDocumentSnapshot document : task.getResult()) {
                    String username = document.getString("username");
                    Double lat = document.getDouble("lat");
                    Double lng = document.getDouble("lng");
                    String uid = document.getId();

                    if (lat != null && lng != null) {
                        LatLng position = new LatLng(lat, lng);
                        MarkerOptions options = new MarkerOptions()
                                .position(position)
                                .title(username);

                        if (uid.equals(currentUid)) {
                            options.icon(BitmapDescriptorFactory.defaultMarker(51.0f));
                            mMap.addMarker(options);
                        } else {
                            Marker marker = mMap.addMarker(options);
                            if (marker != null) {
                                otherMarkers.add(marker);
                            }
                        }
                    }
                }
            }
        });
    }
}