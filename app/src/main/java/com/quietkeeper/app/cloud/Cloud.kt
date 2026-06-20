package com.quietkeeper.app.cloud
object Cloud { @Volatile var sync: CloudSync = LocalCloudSync() }
// To enable real Firebase later:
//  1. Add google-services.json (from your Firebase project) to app/, apply the
//     com.google.gms.google-services plugin, add firebase-firestore + firebase-storage + firebase-auth deps.
//  2. Implement FirebaseCloudSync : CloudSync that writes event metadata to Firestore
//     (collection "events", doc id = integrityHash) and uploads the WAV to Storage.
//  3. Set Cloud.sync = FirebaseCloudSync() in Application.onCreate(). No other code changes.
