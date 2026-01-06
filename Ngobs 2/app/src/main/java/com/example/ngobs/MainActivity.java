package com.example.ngobs;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ImageButton;

import com.example.ngobs.utils.FirebaseUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNavigationView;
    ImageButton searchButton;
    FloatingActionButton aiChatFab; // ✅ Added FAB variable

    ChatFragment chatFragment;
    ProfileFragment profileFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatFragment = new ChatFragment();
        profileFragment = new ProfileFragment();

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        searchButton = findViewById(R.id.main_search_btn);
        aiChatFab = findViewById(R.id.ai_chat_fab); // ✅ Initialize FAB

        // Search button click → open SearchUserActivity
        searchButton.setOnClickListener((v) -> {
            startActivity(new Intent(MainActivity.this, SearchUserActivity.class));
        });

        // FAB click → open AI Chat
        aiChatFab.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ChatActivity.class);
            intent.putExtra("userId", "ai_bot");
            intent.putExtra("username", "Ngobots");// identify chat with AI bot
               // optional flag
            startActivity(intent);
        });

        // BottomNavigation item selected
        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.menu_chat) {
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.main_frame_layout, chatFragment)
                            .commit();
                }
                if (item.getItemId() == R.id.menu_profile) {
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.main_frame_layout, profileFragment)
                            .commit();
                }
                return true;
            }
        });

        // Default selected tab
        bottomNavigationView.setSelectedItemId(R.id.menu_chat);

        // Get Firebase Cloud Messaging token
        getFCMToken();
    }

    // Fetch and save FCM token
    void getFCMToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String token = task.getResult();
                FirebaseUtil.currentUserDetails().update("fcmToken", token);
            }
        });
    }
}
