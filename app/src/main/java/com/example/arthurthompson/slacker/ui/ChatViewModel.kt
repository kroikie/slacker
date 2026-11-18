package com.example.arthurthompson.slacker.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.arthurthompson.slacker.model.Message
import com.google.firebase.Timestamp
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID
import androidx.core.net.toUri

class ChatViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance("default")
    private val storage = FirebaseStorage.getInstance()
    // Configure Firebase AI Logic with limited-use App Check tokens for replay protection
    private val ai = Firebase.ai(
        backend = GenerativeBackend.googleAI(),
        useLimitedUseAppCheckTokens = true
    )

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isCartoonizing = MutableStateFlow(false)
    val isCartoonizing: StateFlow<Boolean> = _isCartoonizing.asStateFlow()

    private val _userPhotoUrl = MutableStateFlow<String?>(null)
    val userPhotoUrl: StateFlow<String?> = _userPhotoUrl.asStateFlow()

    private val _userDisplayName = MutableStateFlow<String?>(null)
    val userDisplayName: StateFlow<String?> = _userDisplayName.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _userPhotoUrl.value = user?.photoUrl?.toString()
        _userDisplayName.value = user?.displayName
        Log.d("ChatViewModel", "Auth state changed: ${user?.uid}, Name: ${user?.displayName}")
    }

    init {
        listenForMessages()
        auth.addAuthStateListener(authStateListener)
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
    }

    private fun listenForMessages() {
        firestore.collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val messageList = snapshot.toObjects(Message::class.java)
                    _messages.value = messageList
                }
            }
    }

    fun sendMessage(text: String) {
        val user = auth.currentUser ?: return
        val message = Message(
            text = text,
            senderName = _userDisplayName.value ?: user.displayName ?: "Anonymous",
            senderUid = user.uid,
            senderPhotoUrl = _userPhotoUrl.value,
            timestamp = Timestamp.now()
        )
        firestore.collection("messages").add(message)
    }

    fun updateProfile(newDisplayName: String) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(newDisplayName)
                    .build()
                user.updateProfile(profileUpdates).await()
                user.reload().await() // Ensure local user object is fresh
                _userDisplayName.value = auth.currentUser?.displayName
                updateUserMessages(displayName = newDisplayName)
                Log.d("ChatViewModel", "Profile updated successfully")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error updating profile", e)
            }
        }
    }

    fun cartoonizeProfilePicture(context: Context) {
        val user = auth.currentUser ?: run {
            Log.d("ChatViewModel", "No user logged in")
            return
        }
        
        // Always try to get the original Google profile picture if available
        val googlePhotoUrl = user.providerData.find { it.providerId == "google.com" }?.photoUrl
        val originalPhotoUrl = googlePhotoUrl ?: user.photoUrl
        
        if (originalPhotoUrl == null) {
            Log.d("ChatViewModel", "User has no photo URL")
            return
        }

        viewModelScope.launch {
            _isCartoonizing.value = true
            try {
                Log.d("ChatViewModel", "Starting cartoonization for: $originalPhotoUrl")
                // 1. Download original image using Coil
                val bitmap = downloadBitmap(context, originalPhotoUrl.toString()) ?: run {
                    Log.d("ChatViewModel", "Failed to download bitmap")
                    return@launch
                }

                // 2. Cartoonize using Imagen
                Log.d("ChatViewModel", "Performing cartoonization with Nano Banana...")
                val cartoonizedBitmap = performCartoonization(bitmap) ?: run {
                    Log.d("ChatViewModel", "Cartoonization returned null")
                    return@launch
                }

                // 3. Upload to Firebase Storage
                Log.d("ChatViewModel", "Uploading cartoonized bitmap to storage...")
                val downloadUrl = uploadToStorage(cartoonizedBitmap) ?: run {
                    Log.d("ChatViewModel", "Failed to upload to storage")
                    return@launch
                }

                // 4. Update Firebase User Profile
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setPhotoUri(downloadUrl.toUri())
                    .build()
                user.updateProfile(profileUpdates).await()
                user.reload().await() // Ensure local user object is fresh

                // 5. Update local state
                Log.d("ChatViewModel", "Cartoonization success! New URL: $downloadUrl")
                _userPhotoUrl.value = auth.currentUser?.photoUrl?.toString()
                updateUserMessages(photoUrl = downloadUrl)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in cartoonization flow", e)
            } finally {
                _isCartoonizing.value = false
            }
        }
    }

    private suspend fun downloadBitmap(context: Context, url: String): Bitmap? {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false) // Required for getting Bitmap
            .build()

        val result = (loader.execute(request) as? SuccessResult)?.drawable
        return (result as? BitmapDrawable)?.bitmap
    }

    private suspend fun performCartoonization(input: Bitmap): Bitmap? {
        // Use Nano Banana via Google Developer AI API
        val model = ai.generativeModel(
            modelName = "gemini-2.5-flash-image",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.TEXT, ResponseModality.IMAGE)
            }
        )

        val prompt = content {
            image(input)
            text("""
                A portrait of the person in the image in 3D-cartoon style with a blurred background.
                A cute and lovely character, with a smiling face, looking at the camera, pastel color tone.
                """.trim())
        }

        return try {
            val response = model.generateContent(prompt)
            Log.d("ChatViewModel", "Nano Banana response received. Candidates: ${response.candidates.size}")
            
            if (response.candidates.isEmpty()) {
                Log.w("ChatViewModel", "No candidates returned (possibly blocked by safety filters)")
                return null
            }

            val imagePart = response.candidates.firstOrNull()?.content?.parts
                ?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            
            if (imagePart == null) {
                Log.w("ChatViewModel", "No image part found in response. Text: ${response.text}")
                return null
            }

            Log.d("ChatViewModel", "Successfully extracted cartoonized image")
            imagePart.image
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error calling Nano Banana", e)
            null
        }
    }

    private suspend fun uploadToStorage(bitmap: Bitmap): String? {
        val uid = auth.currentUser?.uid ?: run {
            Log.d("ChatViewModel", "uploadToStorage: No UID")
            return null
        }
        val path = "avatars/$uid/${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(path)
        
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val data = baos.toByteArray()

        return try {
            Log.d("ChatViewModel", "Uploading bytes to: $path")
            ref.putBytes(data).await()
            val url = ref.downloadUrl.await().toString()
            Log.d("ChatViewModel", "Upload successful, download URL: $url")
            url
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error uploading to storage", e)
            null
        }
    }

    private suspend fun updateUserMessages(displayName: String? = null, photoUrl: String? = null) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val snapshot = firestore.collection("messages")
                .whereEqualTo("senderUid", uid)
                .get()
                .await()

            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                val updates = mutableMapOf<String, Any>()
                displayName?.let { updates["senderName"] = it }
                photoUrl?.let { updates["senderPhotoUrl"] = it }
                if (updates.isNotEmpty()) {
                    batch.update(doc.reference, updates)
                }
            }
            batch.commit().await()
            Log.d("ChatViewModel", "Updated ${snapshot.size()} messages for user $uid")
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error updating messages", e)
        }
    }
}
