package com.example.data

import android.content.Context
import com.example.R
import com.example.model.WatchHistoryItem
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class UserDataRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        require(!uid.isNullOrBlank()) { "Usuário precisa entrar com o Google antes de acessar os dados." }
        return uid
    }

    fun observeHistory(expectedUid: String): Flow<List<WatchHistoryItem>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != expectedUid) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$uid/watch_history"
        emitAll(
            db.collection("users").document(uid).collection("watch_history")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WatchHistoryDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.toWatchHistoryItem() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveHistoryItem(item: WatchHistoryItem): Result<Unit> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Usuário não autenticado."))
        }
        val docId = FirestoreCodec.stableDocId(item.id)
        val docRef = db.collection("users").document(uid).collection("watch_history").document(docId)
        val docPath = docRef.path
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val updatePayload = FirestoreCodec.toHistoryUpdateMap(uid, item)
                docRef.update(updatePayload).await()
            } else {
                val createPayload = FirestoreCodec.toHistoryCreateMap(uid, item)
                docRef.set(createPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncHistoryBatch(items: List<WatchHistoryItem>): Result<Unit> {
        return try {
            val uid = requireUserId()
            items.take(50).forEach { item ->
                val docId = FirestoreCodec.stableDocId(item.id)
                val docRef = db.collection("users").document(uid).collection("watch_history").document(docId)
                val snap = docRef.get().await()
                if (snap.exists()) {
                    docRef.update(FirestoreCodec.toHistoryUpdateMap(uid, item)).await()
                } else {
                    docRef.set(FirestoreCodec.toHistoryCreateMap(uid, item)).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid ?: "anon"}/watch_history")
            Result.failure(e)
        }
    }

    suspend fun removeHistoryItem(itemUrl: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Não autenticado"))
        val docId = FirestoreCodec.stableDocId(itemUrl)
        val docRef = db.collection("users").document(uid).collection("watch_history").document(docId)
        val docPath = docRef.path
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearAllHistory(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Não autenticado"))
        val colPath = "users/$uid/watch_history"
        return try {
            val docs = db.collection("users").document(uid).collection("watch_history").get().await()
            val batch = db.batch()
            docs.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colPath)
            Result.failure(e)
        }
    }

    fun observePreferences(expectedUid: String): Flow<UserPreferencesDoc?> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != expectedUid) {
            emit(null)
            return@flow
        }
        val docRef = db.collection("users").document(uid).collection("preferences").document("default")
        val path = docRef.path
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(UserPreferencesDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun savePreferences(
        zoomPreset: String,
        zoomScale: Double,
        focusX: Double,
        focusY: Double,
        adBlockAutoUpdate: Boolean,
        recentSearches: List<String>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Não autenticado"))
        val docRef = db.collection("users").document(uid).collection("preferences").document("default")
        val path = docRef.path
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val payload = FirestoreCodec.toPreferencesUpdateMap(
                    uid, zoomPreset, zoomScale, focusX, focusY, adBlockAutoUpdate, recentSearches
                )
                docRef.update(payload).await()
            } else {
                val payload = FirestoreCodec.toPreferencesCreateMap(
                    uid, zoomPreset, zoomScale, focusX, focusY, adBlockAutoUpdate, recentSearches
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
