const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_EMAIL = "masfitanha@gmail.com";

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

test("Unauthenticated user: cannot read or write cloud_items or favorites", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("cloud_items").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("favorites").get());
});

test("Authenticated user: can create and read own favorite, cannot read another user's favorite", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("favorites").doc("fav_1").set({
      userId: BOB_UID,
      itemId: "match_101",
      itemType: "match",
      title: "India vs Australia",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("favorites").doc("fav_1").get());

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("favorites").doc("fav_alice").set({
      userId: ALICE_UID,
      itemId: "match_102",
      itemType: "match",
      title: "Bangladesh vs Pakistan",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("favorites").where("userId", "==", ALICE_UID).get()
  );
});

test("Authenticated user: can query public cloud_items, cannot modify another user's cloud_item", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("cloud_items").doc("match_live_1").set({
      ownerId: ALICE_UID,
      itemType: "match",
      visibility: "public",
      title: "IND vs AUS Live Stream",
      payloadJson: "{\"id\":\"match_live_1\"}",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("cloud_items").where("visibility", "==", "public").get()
  );

  // Bob cannot delete Alice's cloud item
  await assertFails(bobDb.collection("cloud_items").doc("match_live_1").delete());
});

test("Email spoofing with email_verified=false cannot bypass ownership", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("cloud_items").doc("match_live_2").set({
      ownerId: ALICE_UID,
      itemType: "match",
      visibility: "public",
      title: "BAN vs ENG",
      payloadJson: "{\"id\":\"match_live_2\"}",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const spoofedAdminDb = testEnv.authenticatedContext(BOB_UID, {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(spoofedAdminDb.collection("cloud_items").doc("match_live_2").delete());
});

test("Client cannot self-assign admin role in /admins/{uid}", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("admins").doc(ALICE_UID).set({ role: "admin" })
  );
});
