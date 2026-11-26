const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("rooms").get());
  await assertFails(unauthDb.collection("invites").doc("ESOSA1").get());
  await assertFails(
    unauthDb.collection("rooms").doc("room_1").set({
      title: "Hack",
      topic: "",
      roomCode: "HACK11",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      lastMessageText: "",
      lastMessageSenderName: "",
      lastMessageAt: new Date(Date.now() - 1000),
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("User profile isolation: owner can create/update/read; another user cannot read PII", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      notificationsEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial update toggles notificationsEnabled and extended profile fields cleanly
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      notificationsEnabled: false,
      username: "alice_dev",
      bio: "Building Esosa real-time sync",
      avatarKey: "ocean",
      themeMode: "dark",
      language: "English",
      fcmToken: "fcm_token_alice_1",
      fcmTokens: ["fcm_token_alice_1"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field / shadow update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot read Alice's private profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Rooms, Invites, and Real-Time Messages: aligned queries, join by invite, and isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date(Date.now() - 3000);

  // Alice creates a room (NO @DocumentId `id` field in payload)
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_1").set({
      title: "Design Lounge",
      topic: "Real-time sync",
      roomCode: "ESOSA1",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      lastMessageText: "Room created",
      lastMessageSenderName: "Alice",
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice creates matching invite lookup
  await assertSucceeds(
    aliceDb.collection("invites").doc("ESOSA1").set({
      roomId: "room_1",
      roomTitle: "Design Lounge",
      ownerId: ALICE_UID,
      createdAt: now,
    })
  );

  // Invites cannot be listed (prevent enumeration)
  await assertFails(bobDb.collection("invites").get());

  // Seed another room owned by Bob that Alice is NOT a member of
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("rooms").doc("bob_private_room").set({
      title: "Bob Private",
      topic: "Secret",
      roomCode: "BOB123",
      ownerId: BOB_UID,
      memberUids: [BOB_UID],
      lastMessageText: "Hi",
      lastMessageSenderName: "Bob",
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    });
  });

  // Unfiltered rooms query fails; array-contains query succeeds
  await assertFails(aliceDb.collection("rooms").get());
  await assertSucceeds(aliceDb.collection("rooms").where("memberUids", "array-contains", ALICE_UID).get());

  // Bob cannot read room before joining
  await assertFails(bobDb.collection("rooms").doc("room_1").get());

  // Bob looks up invite by exact code and joins room
  await assertSucceeds(bobDb.collection("invites").doc("ESOSA1").get());
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_1").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 1500),
    })
  );

  // Bob sends a real-time message with denormalized memberUids
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_1").collection("messages").doc("msg_1").set({
      roomId: "room_1",
      senderId: BOB_UID,
      senderName: "Bob",
      text: "Hey Alice!",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Both Alice & Bob can query messages with array-contains
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_1").collection("messages")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );

  // Charlie (non-member) cannot read room or messages or send messages
  await assertFails(charlieDb.collection("rooms").doc("room_1").get());
  await assertFails(charlieDb.collection("rooms").doc("room_1").collection("messages").get());
  await assertFails(charlieDb.collection("rooms").doc("room_1").collection("messages").doc("msg_1").get());
  await assertFails(
    charlieDb.collection("rooms").doc("room_1").collection("messages").doc("msg_2").set({
      roomId: "room_1",
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      text: "Sneak in",
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      createdAt: new Date(Date.now() - 500),
    })
  );

  // Alice and Bob (room members) can update reactions on Bob's message without modifying immutable fields
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_1").collection("messages").doc("msg_1").update({
      reactions: { "👍": [ALICE_UID] },
    })
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_1").collection("messages").doc("msg_1").update({
      reactions: { "👍": [ALICE_UID, BOB_UID], "🔥": [BOB_UID] },
    })
  );

  // Tampering with message text by non-sender (Alice on Bob's msg) or non-member reaction update fails
  await assertFails(
    aliceDb.collection("rooms").doc("room_1").collection("messages").doc("msg_1").update({
      text: "Edited message",
      reactions: { "👍": [ALICE_UID] },
    })
  );
  await assertFails(
    aliceDb.collection("rooms").doc("room_1").collection("messages").doc("msg_1").update({
      text: "Non-sender hijack",
      isEdited: true,
    })
  );
  await assertFails(
    charlieDb.collection("rooms").doc("room_1").collection("messages").doc("msg_1").update({
      reactions: { "😂": [CHARLIE_UID] },
    })
  );

  // Original sender (Bob) can edit his own message text cleanly
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_1").collection("messages").doc("msg_1").update({
      text: "Hey Alice! (updated)",
      isEdited: true,
    })
  );

  // Voice message metadata allowed
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_1").collection("messages").doc("msg_voice_1").set({
      roomId: "room_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Voice note",
      messageType: "voice",
      voiceDurationSec: 12,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 850),
    })
  );

  // Photo messages with Firebase Storage HTTPS URL succeed (even with empty caption) and allow reactions
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_1").collection("messages").doc("msg_photo_1").set({
      roomId: "room_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "",
      imageUrl: "https://firebasestorage.googleapis.com/v0/b/demo.appspot.com/o/rooms%2Froom_1%2Fphotos%2Fmsg_photo_1.jpg?alt=media",
      storagePath: "rooms/room_1/photos/msg_photo_1.jpg",
      messageType: "image",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 800),
    })
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_1").collection("messages").doc("msg_photo_1").update({
      reactions: { "🔥": [BOB_UID] },
    })
  );
  // Reject non-HTTPS imageUrl, tampering with imageUrl, and empty text with no imageUrl
  await assertFails(
    aliceDb.collection("rooms").doc("room_1").collection("messages").doc("msg_photo_bad_url").set({
      roomId: "room_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "",
      imageUrl: "http://insecure.example/photo.jpg",
      messageType: "image",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 750),
    })
  );
  await assertFails(
    aliceDb.collection("rooms").doc("room_1").collection("messages").doc("msg_empty").set({
      roomId: "room_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 700),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_1").collection("messages").doc("msg_photo_1").update({
      imageUrl: "https://evil.example/other.jpg",
      reactions: { "🔥": [BOB_UID] },
    })
  );

  // Typing indicators: Bob sets typing=true; Alice lists typing statuses; Charlie denied; spoofed userId denied
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_1").collection("typing").doc(BOB_UID).set({
      userId: BOB_UID,
      roomId: "room_1",
      displayName: "Bob",
      typing: true,
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_1").collection("typing").doc(BOB_UID).update({
      displayName: "Bob",
      typing: false,
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_1").collection("typing")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    aliceDb.collection("rooms").doc("room_1").collection("typing").doc(BOB_UID).set({
      userId: BOB_UID,
      roomId: "room_1",
      displayName: "Bob",
      typing: true,
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    charlieDb.collection("rooms").doc("room_1").collection("typing").get()
  );
  await assertFails(
    charlieDb.collection("rooms").doc("room_1").collection("typing")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    charlieDb.collection("rooms").doc("room_1").collection("typing").doc(BOB_UID).get()
  );
  await assertFails(
    charlieDb.collection("rooms").doc("room_1").collection("typing").doc(CHARLIE_UID).set({
      userId: CHARLIE_UID,
      roomId: "room_1",
      displayName: "Charlie",
      typing: true,
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("User-scoped Gemini AI threads & messages: owner succeeds, reactions allowed, cross-user fails, invalid model fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_threads").doc("thread_1").set({
      userId: ALICE_UID,
      title: "Architecture Deep Dive",
      personaId: "reasoning",
      modelName: "gemini-4-carbon",
      systemInstruction: "You are Esosa Deep Thinker.",
      lastMessagePreview: "Let us begin",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_threads").doc("thread_1").collection("messages").doc("m_1").set({
      userId: ALICE_UID,
      threadId: "thread_1",
      role: "user",
      text: "Compare event sourcing vs CRUD.",
      modelName: "gemini-4-carbon",
      createdAt: now,
    })
  );

  // List AI threads & messages succeeds for Alice
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("ai_threads").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("ai_threads").doc("thread_1").collection("messages").get());

  // Alice can react to AI message; tampering with text or cross-user reaction fails
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_threads").doc("thread_1").collection("messages").doc("m_1").update({
      reactions: { "❤️": [ALICE_UID] },
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_threads").doc("thread_1").collection("messages").doc("m_1").update({
      text: "Tampered",
      reactions: { "❤️": [ALICE_UID] },
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("ai_threads").doc("thread_1").collection("messages").doc("m_1").update({
      reactions: { "👍": [BOB_UID] },
    })
  );

  // Cross-user read denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("ai_threads").get());

  // Invalid model name rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_threads").doc("thread_bad").set({
      userId: ALICE_UID,
      title: "Bad Model",
      personaId: "general",
      modelName: "gemini-1.5-pro",
      systemInstruction: "Test",
      lastMessagePreview: "",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("App release metadata (/app_config/release): signed-in get succeeds; unauth, list, and client writes denied", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("app_config").doc("release").set({
      latestVersionCode: 2,
      latestVersionName: "1.0.1",
      releaseNotes: "Automatic update notifications and live voice polish.",
      downloadUrl: "https://github.com/example/Esosa/releases/latest",
      isMandatory: false,
    });
  });

  await assertFails(unauthDb.collection("app_config").doc("release").get());
  await assertSucceeds(aliceDb.collection("app_config").doc("release").get());
  await assertFails(aliceDb.collection("app_config").get());
  await assertFails(
    aliceDb.collection("app_config").doc("release").set({
      latestVersionCode: 99,
      latestVersionName: "9.9.9",
      releaseNotes: "Tamper",
      downloadUrl: "https://evil.example",
    })
  );
});

test("User-scoped Live Voice threads & transcript messages: owner succeeds, cross-user & invalid speaker denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("live_threads").doc("live_1").set({
      userId: ALICE_UID,
      title: "Morning Voice Session",
      modelName: "gemini-3.8-live",
      lastTranscriptPreview: "Listening live",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("live_threads").doc("live_1").collection("messages").doc("lm_1").set({
      userId: ALICE_UID,
      threadId: "live_1",
      speaker: "You",
      text: "Summarize my day",
      createdAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("live_threads").doc("live_1").collection("messages").doc("lm_2").set({
      userId: ALICE_UID,
      threadId: "live_1",
      speaker: "Esosa Live",
      text: "Here is a quick plan for your morning.",
      createdAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("live_threads").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("live_threads").doc("live_1").collection("messages").get());

  // Cross-user read and write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("live_threads").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("live_threads").doc("live_1").collection("messages").get());

  // Invalid speaker rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("live_threads").doc("live_1").collection("messages").doc("lm_bad").set({
      userId: ALICE_UID,
      threadId: "live_1",
      speaker: "Admin",
      text: "Spoof",
      createdAt: now,
    })
  );
});

test("Instagram social posts, reels, likes, comments, stories, saved posts, and follows", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Unauthenticated reads/writes blocked
  await assertFails(unauthDb.collection("posts").get());
  await assertFails(unauthDb.collection("stories").get());
  await assertFails(unauthDb.collection("follows").get());

  // Alice publishes a photo post
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").set({
      authorId: ALICE_UID,
      authorUsername: "alice_studio",
      authorDisplayName: "Alice",
      mediaType: "photo",
      imageUrl: "https://firebasestorage.googleapis.com/v0/b/demo.appspot.com/o/users%2Falice%2Fpost_1.jpg?alt=media",
      storagePath: "users/alice_uid_123/post_1.jpg",
      caption: "Golden hour light ✨ #esosa",
      location: "San Francisco",
      aspectRatio: 1.0,
      likedByUids: [],
      commentCount: 0,
      createdAt: now,
    })
  );

  // Reject creating a post with spoofed authorId or pre-seeded likes
  await assertFails(
    bobDb.collection("posts").doc("post_spoof").set({
      authorId: ALICE_UID,
      authorUsername: "alice_studio",
      authorDisplayName: "Alice",
      mediaType: "photo",
      imageUrl: "https://firebasestorage.googleapis.com/v0/b/demo.appspot.com/o/fake.jpg",
      caption: "Spoof",
      likedByUids: [],
      commentCount: 0,
      createdAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("posts").doc("post_prefake").set({
      authorId: ALICE_UID,
      authorUsername: "alice_studio",
      authorDisplayName: "Alice",
      mediaType: "reel",
      imageUrl: "https://firebasestorage.googleapis.com/v0/b/demo.appspot.com/o/reel.jpg",
      caption: "Fake likes",
      likedByUids: [BOB_UID],
      commentCount: 0,
      createdAt: now,
    })
  );

  // Bob can list feed and like/unlike Alice's post (only his own UID)
  await assertSucceeds(bobDb.collection("posts").get());
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").update({
      likedByUids: [BOB_UID],
    })
  );
  // Bob cannot add Charlie's UID to likedByUids
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({
      likedByUids: [BOB_UID, CHARLIE_UID],
    })
  );
  // Bob cannot edit Alice's caption or delete Alice's post
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({ caption: "Hacked" })
  );
  await assertFails(bobDb.collection("posts").doc("post_1").delete());

  // Bob comments on Alice's post and increments commentCount by 1
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("comments").doc("c_1").set({
      postId: "post_1",
      authorId: BOB_UID,
      authorUsername: "bob_creates",
      text: "Clean framing!",
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").update({ commentCount: 1 })
  );
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({ commentCount: 99 })
  );

  // Alice (post owner) or Bob (comment author) can delete comment; Charlie cannot
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(
    charlieDb.collection("posts").doc("post_1").collection("comments").doc("c_1").delete()
  );
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").collection("comments").doc("c_1").delete()
  );

  // Stories: Alice publishes photo & video stories, Bob marks viewed, Charlie cannot spoof viewedByUids
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set({
      authorId: ALICE_UID,
      authorUsername: "alice_studio",
      caption: "Behind the scenes",
      imageUrl: "https://firebasestorage.googleapis.com/v0/b/demo.appspot.com/o/story_1.jpg",
      viewedByUids: [],
      createdAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_video_1").set({
      authorId: ALICE_UID,
      authorUsername: "alice_studio",
      caption: "Morning coffee clip",
      imageUrl: "https://firebasestorage.googleapis.com/v0/b/demo.appspot.com/o/story_poster.jpg",
      videoUrl: "https://firebasestorage.googleapis.com/v0/b/demo.appspot.com/o/story_video_1.mp4",
      viewedByUids: [],
      createdAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("stories").doc("story_1").update({
      viewedByUids: [BOB_UID],
    })
  );
  await assertFails(
    charlieDb.collection("stories").doc("story_1").update({
      viewedByUids: [BOB_UID, ALICE_UID],
    })
  );

  // Story emoji reactions: signed-in viewers can react; immutable fields & unauthenticated updates fail
  await assertSucceeds(
    bobDb.collection("stories").doc("story_1").update({
      reactions: { "❤️": [BOB_UID] },
    })
  );
  await assertSucceeds(
    charlieDb.collection("stories").doc("story_1").update({
      reactions: { "❤️": [BOB_UID], "🔥": [CHARLIE_UID] },
    })
  );
  await assertFails(
    bobDb.collection("stories").doc("story_1").update({
      caption: "Tampered caption",
      reactions: { "❤️": [BOB_UID] },
    })
  );
  await assertFails(
    unauthDb.collection("stories").doc("story_1").update({
      reactions: { "😂": ["anon"] },
    })
  );

  // Story archiving: author can archive/unarchive and query own stories; non-author cannot archive
  await assertSucceeds(
    aliceDb.collection("stories").where("authorId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").update({
      archived: true,
    })
  );
  await assertFails(
    bobDb.collection("stories").doc("story_1").update({
      archived: false,
    })
  );
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").update({
      archived: false,
    })
  );

  // Story replies: Bob replies to Alice's story; Alice & Bob can read/query; Charlie cannot read or spoof storyAuthorId
  await assertSucceeds(
    bobDb.collection("stories").doc("story_1").collection("replies").doc("reply_1").set({
      storyId: "story_1",
      storyAuthorId: ALICE_UID,
      authorId: BOB_UID,
      authorUsername: "bob_creates",
      text: "Love this shot!",
      createdAt: now,
    })
  );
  await assertFails(
    charlieDb.collection("stories").doc("story_1").collection("replies").doc("reply_spoof").set({
      storyId: "story_1",
      storyAuthorId: CHARLIE_UID,
      authorId: CHARLIE_UID,
      authorUsername: "charlie_evil",
      text: "Spoof",
      createdAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").collection("replies")
      .where("storyAuthorId", "==", ALICE_UID)
      .get()
  );
  await assertSucceeds(
    bobDb.collection("stories").doc("story_1").collection("replies")
      .where("authorId", "==", BOB_UID)
      .get()
  );
  await assertFails(
    charlieDb.collection("stories").doc("story_1").collection("replies")
      .where("storyAuthorId", "==", ALICE_UID)
      .get()
  );

  // Saved posts: strictly private to owner
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("saved_posts").doc("post_1").set({
      userId: BOB_UID,
      postId: "post_1",
      createdAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("saved_posts").get()
  );

  // Follow graph: Bob follows Alice, cannot follow himself or spoof Alice following Bob
  const followId = `${BOB_UID}_${ALICE_UID}`;
  await assertSucceeds(
    bobDb.collection("follows").doc(followId).set({
      followerId: BOB_UID,
      targetUid: ALICE_UID,
      targetUsername: "alice_studio",
      createdAt: now,
    })
  );
  await assertFails(
    bobDb.collection("follows").doc(`${BOB_UID}_${BOB_UID}`).set({
      followerId: BOB_UID,
      targetUid: BOB_UID,
      targetUsername: "bob_creates",
      createdAt: now,
    })
  );
  await assertFails(aliceDb.collection("follows").doc(followId).delete());
  await assertSucceeds(bobDb.collection("follows").doc(followId).delete());
});

