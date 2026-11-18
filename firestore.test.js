const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const OWNER_UID = "owner_uid_999";
const OWNER_EMAIL = "tusharade31@gmail.com";

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

const pastDate = (offsetMs = 2000) => new Date(Date.now() - offsetMs);

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(unauthDb.collection("app_config").doc("system").get());
  await assertFails(unauthDb.collection("admin_logs").get());
});

test("User profile create, partial update, cross-user isolation, and ghost-field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      preferredModel: "gemini-3.8-flash",
      preferredLanguage: "en-US",
      totalPrompts: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update allowed fields only
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredModel: "gemini-3.5-flash-lite",
      totalPrompts: 5,
      updatedAt: pastDate(1000),
    })
  );

  // Reject ghost field / role escalation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Reject future timestamp tampering
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Future Alice",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user PII isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
});

test("Conversations, Messages, Knowledge, Artworks, Projects, Tasks, and Search History match Kotlin payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const t0 = pastDate(8000);
  const t1 = pastDate(1000);

  // Conversation + Message
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").set({
      title: "Anime Design Session",
      modelUsed: "gemini-3.8-flash",
      language: "en-US",
      lastMessagePreview: "Draw Mikey pencil sketch",
      messageCount: 1,
      createdAt: t0,
      updatedAt: t0,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1")
      .collection("messages").doc("msg_1").set({
        role: "user",
        text: "Draw Mikey pencil sketch",
        hasSources: false,
        sources: [],
        createdAt: t0,
      })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );

  // Knowledge item
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("knowledge").doc("k_1").set({
      title: "Preferred Anime Style",
      content: "4B graphite cross-hatching with dramatic lighting",
      folder: "Memory",
      itemType: "memory",
      pinned: true,
      createdAt: t0,
      updatedAt: t0,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("knowledge").doc("k_1").update({
      pinned: false,
      updatedAt: t1,
    })
  );

  // Artwork item (no `id` field in map!)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("artworks").doc("art_1").set({
      title: "Mikey Inspired Sketch",
      prompt: "4B graphite pencil portrait",
      universe: "Tokyo Revengers",
      style: "Graphite Pencil Sketch",
      pencilGrade: "4B",
      collectionName: "Tokyo Revengers",
      localFilePath: "/data/user/0/app/files/artworks/art_1.png",
      favorite: true,
      createdAt: t0,
      updatedAt: t0,
    })
  );
  // Reject @DocumentId `id` key in write payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("artworks").doc("art_bad").set({
      id: "art_bad",
      title: "Bad",
      prompt: "Bad",
      universe: "Original",
      style: "Sketch",
      pencilGrade: "HB",
      collectionName: "All",
      localFilePath: "",
      favorite: false,
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Project
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1").set({
      title: "Neo Tokyo Chronicles",
      projectType: "anime_series",
      categoryTag: "Action",
      summary: "Original cyberpunk anime series bible",
      contentText: "Episode 1: Awakening in Shibuya...",
      status: "active",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Task
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      title: "Finish manga chapter 1",
      notes: "Add speedlines to panel 3",
      priority: "high",
      dueLabel: "Today 8 PM",
      completed: false,
      createdAt: t0,
      updatedAt: t0,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      completed: true,
      updatedAt: t1,
    })
  );

  // Search history
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("search_history").doc("s_1").set({
      query: "Tokyo Revengers manga",
      summary: "Japanese manga series written by Ken Wakui.",
      sourceCount: 3,
      createdAt: t0,
    })
  );
});

test("Owner Admin Dashboard RBAC and Email Spoofing defense", async () => {
  const spoofedOwnerDb = testEnv.authenticatedContext("spoof_uid", {
    email: OWNER_EMAIL,
    email_verified: false, // Spoofed unverified email!
  }).firestore();

  const verifiedOwnerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();

  const regularUserDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  // Spoofed email MUST be rejected
  await assertFails(spoofedOwnerDb.collection("users").get());
  await assertFails(
    spoofedOwnerDb.collection("app_config").doc("system").set({
      maintenanceMode: true,
      animeStudioEnabled: true,
      webSearchEnabled: true,
      codingStudioEnabled: true,
      updatedBy: "spoof_uid",
      updatedAt: pastDate(1000),
    })
  );

  // Regular user cannot modify system config or list admin logs
  await assertFails(
    regularUserDb.collection("app_config").doc("system").set({
      maintenanceMode: true,
      animeStudioEnabled: true,
      webSearchEnabled: true,
      codingStudioEnabled: true,
      updatedBy: ALICE_UID,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(regularUserDb.collection("admin_logs").get());

  // Verified owner can manage system config, list users, and read admin logs
  await assertSucceeds(
    verifiedOwnerDb.collection("app_config").doc("system").set({
      maintenanceMode: false,
      animeStudioEnabled: true,
      webSearchEnabled: true,
      codingStudioEnabled: true,
      updatedBy: OWNER_UID,
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(verifiedOwnerDb.collection("users").get());
  await assertSucceeds(verifiedOwnerDb.collection("admin_logs").get());

  // Signed-in user can read app_config/system and create own error log
  await assertSucceeds(regularUserDb.collection("app_config").doc("system").get());
  await assertSucceeds(
    regularUserDb.collection("admin_logs").doc("log_1").set({
      actorUid: ALICE_UID,
      actorEmail: "alice@example.com",
      action: "ai_error",
      details: "Sample error log",
      severity: "error",
      createdAt: pastDate(1000),
    })
  );
});
