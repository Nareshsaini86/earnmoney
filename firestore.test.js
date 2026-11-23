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

test("Unauthenticated user: cannot read or write any user collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection(`users/${ALICE_UID}/automatons`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/revenueEvents`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/withdrawals`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/automatons`).doc("node_1").set({
      userId: ALICE_UID,
      name: "Alpha Node",
      monetizationModel: "X402_API",
      genesisPrompt: "Serve x402 paid data endpoints safely.",
      survivalTier: "NORMAL",
      earnedUsdc: 0,
      computeBurnUsdc: 0,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("AutomatonNode: owner create, partial update, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Valid create matching Kotlin AutomatonNode payload (no `id` key)
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/automatons`).doc("node_1").set({
      userId: ALICE_UID,
      name: "Conway-x402-Alpha",
      walletAddress: "0x742d35Cc6634C0532925a3b844Bc454e4438f44e",
      monetizationModel: "X402_API",
      genesisPrompt: "Expose paid JSON analytics via x402 micropayments on Base.",
      survivalTier: "THRIVING",
      earnedUsdc: 42.5,
      computeBurnUsdc: 8.2,
      endpointUrl: "https://api.example.com/x402",
      active: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid list & partial update
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/automatons`).get());
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/automatons`).doc("node_1").update({
      earnedUsdc: 55.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field / @DocumentId `id` field / createdAt mutation / future timestamp
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/automatons`).doc("node_2").set({
      id: "node_2",
      userId: ALICE_UID,
      name: "Ghost Field Node",
      monetizationModel: "X402_API",
      genesisPrompt: "Expose paid JSON analytics via x402.",
      survivalTier: "NORMAL",
      earnedUsdc: 0,
      computeBurnUsdc: 0,
      active: true,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/automatons`).doc("node_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/automatons`).doc("node_3").set({
      userId: ALICE_UID,
      name: "Future Timestamp",
      monetizationModel: "X402_API",
      genesisPrompt: "Expose paid JSON analytics via x402.",
      survivalTier: "NORMAL",
      earnedUsdc: 0,
      computeBurnUsdc: 0,
      active: true,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection(`users/${ALICE_UID}/automatons`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/automatons`).doc("node_1").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/automatons`).doc("node_1").update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("RevenueEvent: owner create & query succeed; invalid amounts/mutations rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/revenueEvents`).doc("evt_1").set({
      userId: ALICE_UID,
      automatonId: "node_1",
      automatonName: "Conway-x402-Alpha",
      sourceChannel: "X402_MICROPAYMENT",
      entryType: "EARNING",
      amountUsdc: 12.5,
      usdToInrRate: 84.5,
      amountInr: 1056.25,
      txReference: "0xabc123",
      note: "x402 batch settlement",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/revenueEvents`).get());

  // Negative amount rejected
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/revenueEvents`).doc("evt_bad").set({
      userId: ALICE_UID,
      automatonId: "node_1",
      automatonName: "Conway-x402-Alpha",
      sourceChannel: "X402_MICROPAYMENT",
      entryType: "EARNING",
      amountUsdc: -5,
      usdToInrRate: 84.5,
      amountInr: -422.5,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("WithdrawalRequest: lifecycle transitions and terminal state locking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Must start in REQUESTED status and >= 100 INR
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/withdrawals`).doc("wd_bad_status").set({
      userId: ALICE_UID,
      destinationType: "UPI",
      destinationLabel: "naresh@okaxis",
      beneficiaryName: "Naresh Saini",
      amountInr: 500,
      amountUsdcEquivalent: 5.91,
      usdToInrRate: 84.5,
      status: "SETTLED",
      referenceCode: "WD-1001",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/withdrawals`).doc("wd_small").set({
      userId: ALICE_UID,
      destinationType: "UPI",
      destinationLabel: "naresh@okaxis",
      beneficiaryName: "Naresh Saini",
      amountInr: 50,
      amountUsdcEquivalent: 0.59,
      usdToInrRate: 84.5,
      status: "REQUESTED",
      referenceCode: "WD-1002",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid withdrawal creation
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/withdrawals`).doc("wd_1").set({
      userId: ALICE_UID,
      destinationType: "UPI",
      destinationLabel: "naresh@okaxis",
      beneficiaryName: "Naresh Saini",
      amountInr: 1500,
      amountUsdcEquivalent: 17.75,
      usdToInrRate: 84.5,
      status: "REQUESTED",
      referenceCode: "WD-8821",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cannot tamper with amountInr during update
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/withdrawals`).doc("wd_1").update({
      amountInr: 999999,
      status: "PROCESSING",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Valid status transitions: REQUESTED -> PROCESSING -> SETTLED
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/withdrawals`).doc("wd_1").update({
      status: "PROCESSING",
      updatedAt: new Date(Date.now() - 2000),
    })
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/withdrawals`).doc("wd_1").update({
      status: "SETTLED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: SETTLED cannot be changed again or deleted
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/withdrawals`).doc("wd_1").update({
      status: "CANCELLED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(aliceDb.collection(`users/${ALICE_UID}/withdrawals`).doc("wd_1").delete());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/withdrawals`).doc("wd_1").get());
});
