package com.example.ngobs;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.ngobs.adapter.ChatRecyclerAdapter;
import com.example.ngobs.model.ChatMessageModel;
import com.example.ngobs.model.ChatroomModel;
import com.example.ngobs.model.UserModel;
import com.example.ngobs.utils.AndroidUtil;
import com.example.ngobs.utils.FirebaseUtil;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.Timestamp;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Executor;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatActivity extends AppCompatActivity {

    UserModel otherUser;
    String chatroomId;
    ChatroomModel chatroomModel;
    ChatRecyclerAdapter adapter;

    boolean isAiChat;

    EditText messageInput;
    ImageButton sendMessageBtn;
    ImageButton backBtn;
    TextView otherUsername;
    RecyclerView recyclerView;
    ImageView imageView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        //get UserModel
        otherUser = AndroidUtil.getUserModelFromIntent(getIntent());
        Log.d("CHAT_DEBUG", "Opened chat with: " + otherUser.getUserId() + " | username: " + otherUser.getUsername());

        chatroomId = FirebaseUtil.getChatroomId(FirebaseUtil.currentUserId(),otherUser.getUserId());
        isAiChat = "ai_bot".equals(otherUser.getUserId());

        messageInput = findViewById(R.id.chat_message_input);
        sendMessageBtn = findViewById(R.id.message_send_btn);
        backBtn = findViewById(R.id.back_btn);
        otherUsername = findViewById(R.id.other_username);
        recyclerView = findViewById(R.id.chat_recycler_view);
        imageView = findViewById(R.id.profile_pic_image_view);

        if (!isAiChat) {
            FirebaseUtil.getOtherProfilePicStorageRef(otherUser.getUserId())
                    .getDownloadUrl()
                    .addOnCompleteListener(t -> {
                        if (t.isSuccessful()) {
                            Uri uri = t.getResult();
                            AndroidUtil.setProfilePic(this, uri, imageView);
                        }
                    });
        }

        backBtn.setOnClickListener((v)->{
            onBackPressed();
        });
        otherUsername.setText(otherUser.getUsername());

        sendMessageBtn.setOnClickListener((v -> {
            String message = messageInput.getText().toString().trim();
            if(message.isEmpty())
                return;
            sendMessageToUser(message);

            if (isAiChat) {
                sendMessageToAiLogic(message);
            }
        }));

        getOrCreateChatroomModel();
        setupChatRecyclerView();
    }

    void setupChatRecyclerView(){
        Query query = FirebaseUtil.getChatroomMessageReference(chatroomId)
                .orderBy("timestamp", Query.Direction.DESCENDING);

        FirestoreRecyclerOptions<ChatMessageModel> options = new FirestoreRecyclerOptions.Builder<ChatMessageModel>()
                .setQuery(query,ChatMessageModel.class).build();

        adapter = new ChatRecyclerAdapter(options,getApplicationContext());
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setReverseLayout(true);
        recyclerView.setLayoutManager(manager);
        recyclerView.setAdapter(adapter);
        adapter.startListening();
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                recyclerView.smoothScrollToPosition(0);
            }
        });
    }

    void sendMessageToUser(String message){

        if (chatroomModel == null) return;
        chatroomModel.setLastMessageTimestamp(Timestamp.now());
        chatroomModel.setLastMessageSenderId(FirebaseUtil.currentUserId());
        chatroomModel.setLastMessage(message);
        FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel);

        ChatMessageModel chatMessageModel = new ChatMessageModel(message,FirebaseUtil.currentUserId(),Timestamp.now(),false);
        FirebaseUtil.getChatroomMessageReference(chatroomId).add(chatMessageModel)
                .addOnCompleteListener(new OnCompleteListener<DocumentReference>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentReference> task) {
                        if(task.isSuccessful()){
                            messageInput.setText("");
                            if (!isAiChat) {
                                sendNotification(message);
                            }
                        }
                    }
                });
    }

    void getOrCreateChatroomModel(){
        FirebaseUtil.getChatroomReference(chatroomId).get().addOnCompleteListener(task -> {
            if(task.isSuccessful()){
                chatroomModel = task.getResult().toObject(ChatroomModel.class);
                if(chatroomModel==null){
                    //first time chat
                    chatroomModel = new ChatroomModel(
                            chatroomId,
                            Arrays.asList(FirebaseUtil.currentUserId(),otherUser.getUserId()),
                            Timestamp.now(),
                            ""
                    );
                    FirebaseUtil.getChatroomReference(chatroomId).set(chatroomModel);
                }
            }
        });
    }

    void sendNotification(String message){

       FirebaseUtil.currentUserDetails().get().addOnCompleteListener(task -> {
           if(task.isSuccessful()){
               UserModel currentUser = task.getResult().toObject(UserModel.class);
               try{
                   JSONObject jsonObject  = new JSONObject();

                   JSONObject notificationObj = new JSONObject();
                   notificationObj.put("title",currentUser.getUsername());
                   notificationObj.put("body",message);

                   JSONObject dataObj = new JSONObject();
                   dataObj.put("userId",currentUser.getUserId());

                   jsonObject.put("notification",notificationObj);
                   jsonObject.put("data",dataObj);
                   jsonObject.put("to",otherUser.getFcmToken());

                   callApi(jsonObject);


               }catch (Exception e){

               }

           }
       });

    }

    private final Executor executor = new Executor() {
        @Override
        public void execute(Runnable command) {
            ChatActivity.this.runOnUiThread(command);
        }
    };


    void sendMessageToAiLogic(String userMessage) {
        ChatMessageModel typingMessage = new ChatMessageModel("Let me think...", "ai_bot", Timestamp.now(), true);
        FirebaseUtil.getChatroomMessageReference(chatroomId)
                .add(typingMessage);
        // 1. Initialize the base Generative Model
        GenerativeModel ai = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                .generativeModel("gemini-2.5-flash");

        // 2. Create the GenerativeModelFutures compatibility layer
        GenerativeModelFutures model = GenerativeModelFutures.from(ai);

        // 3. Provide a prompt (Content)
        Content prompt = new Content.Builder()
                .addText(userMessage)
                .build();

        // 4. Generate content, which returns a ListenableFuture
        ListenableFuture<GenerateContentResponse> response = model.generateContent(prompt);

        // 5. Attach the callback to handle success or failure
        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                // This executes on the provided executor (main thread)
                String aiReply = result.getText();
                if (aiReply != null && !aiReply.trim().isEmpty()) {
                    saveAiMessage(aiReply.trim());
                } else {
                    saveAiMessage("The AI could not generate a valid response.");
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                // This executes on the provided executor (main thread)
                Log.e("ChatActivity", "AI Generation Failed (ListenableFuture)", t);
                AndroidUtil.showToast(getApplicationContext(), "Error: Failed to get AI response.");
                saveAiMessage("Error: The AI assistant is currently unavailable.");
            }
        }, executor); // IMPORTANT: Pass the executor here
    }

    void replaceTypingMessage(String aiMessage) {
        // Query the last message from AI in this chatroom that is a typing bubble
        FirebaseUtil.getChatroomMessageReference(chatroomId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        for (DocumentSnapshot docSnap : querySnapshot.getDocuments()) {
                            ChatMessageModel model = docSnap.toObject(ChatMessageModel.class);
                            if (model != null && model.isTyping()) {
                                // Replace typing bubble with actual AI message
                                ChatMessageModel updatedMessage = new ChatMessageModel(
                                        aiMessage,
                                        "ai_bot",
                                        model.getTimestamp() != null ? model.getTimestamp() : Timestamp.now(),
                                        false
                                );
                                docSnap.getReference().set(updatedMessage); // Use getReference() to write back
                                break;
                            }
                        }
                    } else {
                        // Fallback: just add AI message if typing bubble not found
                        saveAiMessage(aiMessage);
                    }
                });
    }


    void saveAiMessage(String aiMessage) {
        if (chatroomModel == null) return;
        ChatMessageModel chatMessageModel =
                new ChatMessageModel(
                        aiMessage,
                        "ai_bot",
                        Timestamp.now(),
                        false
                );

        FirebaseUtil.getChatroomMessageReference(chatroomId)
                .add(chatMessageModel);

        chatroomModel.setLastMessage(aiMessage);
        chatroomModel.setLastMessageSenderId("ai_bot");
        chatroomModel.setLastMessageTimestamp(Timestamp.now());

        FirebaseUtil.getChatroomReference(chatroomId)
                .set(chatroomModel);
    }



    void callApi(JSONObject jsonObject){
         MediaType JSON = MediaType.get("application/json; charset=utf-8");
         OkHttpClient client = new OkHttpClient();
        String url = "https://fcm.googleapis.com/fcm/send";
        RequestBody body = RequestBody.create(jsonObject.toString(),JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Authorization","Bearer YOUR_API_KEY")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {

            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

            }
        });

    }

}