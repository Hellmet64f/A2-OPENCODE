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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user cannot read or write watch_history or preferences", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("watch_history").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("watch_history")
      .doc("item_1")
      .set({
        userId: ALICE_UID,
        title: "Batman",
        itemUrl: "https://www.pobreflixtv.delivery/filmes/batman",
        currentUrl: "https://www.pobreflixtv.delivery/filmes/batman",
        mediaType: "movie",
        season: 1,
        episodeNumber: 1,
        favorite: false,
        completed: false,
        progressSeconds: 0,
        createdAt: now,
        updatedAt: now,
      })
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("preferences")
      .doc("settings")
      .get()
  );
});

test("Authenticated owner can create, update, list, and delete watch_history", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("watch_history")
    .doc("series_hash_123");

  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      title: "Breaking Bad",
      coverUrl: "https://example.com/poster.jpg",
      itemUrl: "https://www.pobreflixtv.delivery/series/breaking-bad",
      currentUrl: "https://www.pobreflixtv.delivery/episodios/online/breaking-bad-1x1",
      mediaType: "series",
      season: 1,
      episodeNumber: 1,
      episodeTitle: "Pilot",
      watchedEpisodes: ["1x1"],
      favorite: true,
      completed: false,
      progressSeconds: 120,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    docRef.update({
      season: 1,
      episodeNumber: 2,
      currentUrl: "https://www.pobreflixtv.delivery/episodios/online/breaking-bad-1x2",
      episodeTitle: "Cat's in the Bag...",
      watchedEpisodes: ["1x1", "1x2"],
      completed: true,
      progressSeconds: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("watch_history").get()
  );

  // Verify anime mediaType is allowed for owner
  const animeRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("watch_history")
    .doc("anime_hash_1");
  await assertSucceeds(
    animeRef.set({
      userId: ALICE_UID,
      title: "Dragon Ball DAIMA",
      coverUrl: "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx170083.png",
      itemUrl: "https://anichan.to/anime/170083/dragon-ball-daima",
      currentUrl: "https://anichan.to/anime/170083/dragon-ball-daima/1",
      mediaType: "anime",
      season: 1,
      episodeNumber: 1,
      episodeTitle: "Episódio 1",
      watchedEpisodes: ["1x1"],
      favorite: true,
      completed: false,
      progressSeconds: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject modifying immutable createdAt
  await assertFails(
    docRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject shadow/ghost field on create
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("watch_history")
      .doc("bad_doc")
      .set({
        id: "bad_doc",
        userId: ALICE_UID,
        title: "Test",
        itemUrl: "https://example.com/1",
        currentUrl: "https://example.com/1",
        mediaType: "movie",
        season: 1,
        episodeNumber: 1,
        favorite: false,
        completed: false,
        progressSeconds: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("watch_history")
      .doc("future_doc")
      .set({
        userId: ALICE_UID,
        title: "Test",
        itemUrl: "https://example.com/2",
        currentUrl: "https://example.com/2",
        mediaType: "movie",
        season: 1,
        episodeNumber: 1,
        favorite: false,
        completed: false,
        progressSeconds: 0,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  await assertSucceeds(docRef.delete());
});

test("Cross-user isolation blocks reading or writing another user's watch_history", async () => {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("watch_history")
      .doc("bob_item")
      .set({
        userId: BOB_UID,
        title: "Bob Movie",
        itemUrl: "https://example.com/bob",
        currentUrl: "https://example.com/bob",
        mediaType: "movie",
        season: 1,
        episodeNumber: 1,
        favorite: false,
        completed: false,
        progressSeconds: 0,
        createdAt: now,
        updatedAt: now,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("watch_history").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("watch_history")
      .doc("bob_item")
      .get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("watch_history")
      .doc("bob_item")
      .update({ favorite: true, updatedAt: new Date(Date.now() - 500) })
  );
});

test("Authenticated owner can create and update preferences", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const prefRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("preferences")
    .doc("settings");

  await assertSucceeds(
    prefRef.set({
      userId: ALICE_UID,
      zoomPreset: "normal",
      zoomScale: 1.0,
      focusX: 0.5,
      focusY: 0.5,
      adBlockAutoUpdate: true,
      recentSearches: ["batman", "the boys"],
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    prefRef.update({
      zoomPreset: "cinema",
      zoomScale: 1.3,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject out-of-range zoomScale
  await assertFails(
    prefRef.update({
      zoomScale: 9.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
