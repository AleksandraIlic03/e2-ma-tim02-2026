package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.adapters.FriendRequestAdapter;
import com.example.slagalica.adapters.FriendsAdapter;
import com.example.slagalica.adapters.UserSearchAdapter;
import com.example.slagalica.models.FriendRequest;
import com.example.slagalica.models.RankingEntry;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.List;

public class FriendsActivity extends AppCompatActivity {

    private EditText etSearchUsername;
    private ImageButton btnSearch, btnScanQR;
    private TabLayout tabLayoutFriends;
    private RecyclerView rvFriends;

    private FirebaseFirestore db;
    private String currentUserId;
    private String currentUsername;

    private List<RankingEntry> friendsList = new ArrayList<>();
    private List<String> currentFriendIds = new ArrayList<>();
    private List<FriendRequest> requestsList = new ArrayList<>();
    private List<RankingEntry> searchResults = new ArrayList<>();
    private List<String> pendingSentRequests = new ArrayList<>();

    private FriendsAdapter friendsAdapter;
    private FriendRequestAdapter requestAdapter;
    private UserSearchAdapter searchAdapter;

    private ListenerRegistration friendsListListener, friendsDetailsListener, requestsListener, sentRequestsListener;

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if(result.getContents() != null) {
                    searchAndAddFriend(result.getContents());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();
        fetchCurrentUsername();

        initViews();
        setupRecyclerViews();
        setupTabs();

        btnSearch.setOnClickListener(v -> searchUsers());
        btnScanQR.setOnClickListener(v -> scanQR());

        listenToFriends();
        listenToRequests();
        listenToSentRequests();
    }

    private void listenToSentRequests() {
        if (currentUserId == null) return;
        sentRequestsListener = db.collection("friendRequests")
                .whereEqualTo("fromUserId", currentUserId)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null) return;
                    pendingSentRequests.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        pendingSentRequests.add(doc.getString("toUserId"));
                    }
                    searchAdapter.notifyDataSetChanged();
                });
    }

    private void fetchCurrentUsername() {
        if (currentUserId == null) return;
        db.collection("users").document(currentUserId).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                currentUsername = documentSnapshot.getString("username");
            }
        });
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        etSearchUsername = findViewById(R.id.etSearchUsername);
        btnSearch = findViewById(R.id.btnSearch);
        btnScanQR = findViewById(R.id.btnScanQR);
        tabLayoutFriends = findViewById(R.id.tabLayoutFriends);
        rvFriends = findViewById(R.id.rvFriends);
    }

    private void setupRecyclerViews() {
        rvFriends.setLayoutManager(new LinearLayoutManager(this));
        
        friendsAdapter = new FriendsAdapter(friendsList, friend -> {
            // Start friendly match
            Intent intent = new Intent(this, WaitingRoomActivity.class);
            intent.putExtra("isFriendly", true);
            intent.putExtra("opponentId", friend.getUserId());
            startActivity(intent);
        });

        requestAdapter = new FriendRequestAdapter(requestsList, new FriendRequestAdapter.OnRequestActionListener() {
            @Override
            public void onAccept(FriendRequest request) {
                FriendsManager.acceptFriendRequest(request.getRequestId(), request.getFromUserId(), new FriendsManager.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(FriendsActivity.this, "Zahtev prihvaćen", Toast.LENGTH_SHORT).show();
                        // Prebaci na prvi tab da korisnik vidi novog prijatelja
                        tabLayoutFriends.selectTab(tabLayoutFriends.getTabAt(0));
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(FriendsActivity.this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onReject(FriendRequest request) {
                FriendsManager.rejectFriendRequest(request.getRequestId(), new FriendsManager.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(FriendsActivity.this, "Zahtev odbijen", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(FriendsActivity.this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        searchAdapter = new UserSearchAdapter(searchResults, pendingSentRequests, new UserSearchAdapter.OnUserSearchClickListener() {
            @Override
            public void onAddFriend(RankingEntry user) {
                FriendsManager.sendFriendRequest(user.getUserId(), user.getUsername(), currentUsername != null ? currentUsername : "Korisnik", new FriendsManager.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(FriendsActivity.this, "Zahtev poslat", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(FriendsActivity.this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelRequest(RankingEntry user) {
                FriendsManager.cancelFriendRequest(user.getUserId(), new FriendsManager.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(FriendsActivity.this, "Zahtev otkazan", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(FriendsActivity.this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        rvFriends.setAdapter(friendsAdapter);
    }

    private void setupTabs() {
        tabLayoutFriends.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    rvFriends.setAdapter(friendsAdapter);
                    listenToFriends(); // Ponovo pokreni osluškivanje za svaki slučaj
                } else {
                    rvFriends.setAdapter(requestAdapter);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void listenToFriends() {
        if (currentUserId == null) return;
        if (friendsListListener != null) friendsListListener.remove();
        friendsListListener = db.collection("users").document(currentUserId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null) return;
                    List<String> friendIds = (List<String>) snapshot.get("friends");
                    currentFriendIds.clear();
                    if (friendIds != null) {
                        currentFriendIds.addAll(friendIds);
                    }
                    
                    // Filter out search results that are now friends
                    List<RankingEntry> toRemove = new ArrayList<>();
                    for (RankingEntry user : searchResults) {
                        if (currentFriendIds.contains(user.getUserId())) {
                            toRemove.add(user);
                        }
                    }
                    if (!toRemove.isEmpty()) {
                        searchResults.removeAll(toRemove);
                        searchAdapter.notifyDataSetChanged();
                    }

                    if (friendIds == null || friendIds.isEmpty()) {
                        friendsList.clear();
                        friendsAdapter.notifyDataSetChanged();
                        return;
                    }
                    listenToFriendsDetails(friendIds);
                });
    }

    private void listenToFriendsDetails(List<String> friendIds) {
        if (friendsDetailsListener != null) friendsDetailsListener.remove();
        
        friendsDetailsListener = db.collection("users").whereIn("__name__", friendIds)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null) return;
                    friendsList.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Long starsLong = doc.getLong("stars");
                        Long leagueLong = doc.getLong("league");
                        RankingEntry friend = new RankingEntry(
                                doc.getId(),
                                doc.getString("username"),
                                doc.getString("avatarUrl"),
                                starsLong != null ? starsLong : 0,
                                leagueLong != null ? leagueLong.intValue() : 0,
                                Boolean.TRUE.equals(doc.getBoolean("isOnline")),
                                Boolean.TRUE.equals(doc.getBoolean("isInGame"))
                        );
                        friendsList.add(friend);
                    }
                    friendsAdapter.notifyDataSetChanged();
                });
    }

    private void listenToRequests() {
        if (currentUserId == null) return;
        requestsListener = db.collection("friendRequests")
                .whereEqualTo("toUserId", currentUserId)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null) return;
                    requestsList.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        FriendRequest req = doc.toObject(FriendRequest.class);
                        if (req != null) {
                            req.setRequestId(doc.getId());
                            // We need the fromUsername, it might not be in the request doc
                            // Actually I added it to the model but not necessarily to the Firestore write
                            // Let's fetch it if missing
                            if (req.getFromUsername() == null) {
                                db.collection("users").document(req.getFromUserId()).get()
                                        .addOnSuccessListener(uDoc -> {
                                            req.setFromUsername(uDoc.getString("username"));
                                            requestAdapter.notifyDataSetChanged();
                                        });
                            }
                            requestsList.add(req);
                        }
                    }
                    requestAdapter.notifyDataSetChanged();
                });
    }

    private void searchUsers() {
        String query = etSearchUsername.getText().toString().trim();
        if (query.isEmpty()) return;

        db.collection("users")
                .whereEqualTo("username", query)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    searchResults.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        if (doc.getId().equals(currentUserId)) continue;
                        if (currentFriendIds.contains(doc.getId())) continue;

                        searchResults.add(new RankingEntry(
                                doc.getId(),
                                doc.getString("username"),
                                doc.getString("avatarUrl"),
                                0
                        ));
                    }
                    rvFriends.setAdapter(searchAdapter);
                    searchAdapter.notifyDataSetChanged();
                });
    }

    private void scanQR() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Skeniraj QR kod prijatelja");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(CaptureActivityPortrait.class);
        barcodeLauncher.launch(options);
    }

    private void searchAndAddFriend(String scanContent) {
        // Assume scanContent is userId or username
        // Try as userId first
        db.collection("users").document(scanContent).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                sendFriendRequest(doc.getId(), doc.getString("username"));
            } else {
                // Try as username
                db.collection("users").whereEqualTo("username", scanContent).get().addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot userDoc = querySnapshot.getDocuments().get(0);
                        sendFriendRequest(userDoc.getId(), userDoc.getString("username"));
                    } else {
                        Toast.makeText(this, "Korisnik nije pronađen", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void sendFriendRequest(String userId, String username) {
        FriendsManager.sendFriendRequest(userId, username, currentUsername, new FriendsManager.ActionCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(FriendsActivity.this, "Zahtev poslat", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(FriendsActivity.this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (friendsListListener != null) friendsListListener.remove();
        if (friendsDetailsListener != null) friendsDetailsListener.remove();
        if (requestsListener != null) requestsListener.remove();
        if (sentRequestsListener != null) sentRequestsListener.remove();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
