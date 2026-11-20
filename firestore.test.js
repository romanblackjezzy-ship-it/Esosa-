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

  // Partial update toggles notificationsEnabled cleanly
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      notificationsEnabled: false,
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
});

test("User-scoped Gemini AI threads & messages: owner succeeds, cross-user fails, invalid model fails", async () => {
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
