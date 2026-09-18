import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { readFileSync } from 'fs';
import { resolve } from 'path';

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "p-block-rules-test",
    database: {
      rules: readFileSync(resolve(__dirname, '../../database.rules.json'), 'utf8'),
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearDatabase();
});

describe("P-BLOCK Firebase Rules", () => {
  it("allows device to read/write its own node", async () => {
    const devContext = testEnv.authenticatedContext("device123");
    const db = devContext.database();
    
    await assertSucceeds(db.ref("devices/device123/state").set("ACTIVE"));
    await assertSucceeds(db.ref("devices/device123/state").get());
  });

  it("denies device to read/write other nodes", async () => {
    const devContext = testEnv.authenticatedContext("device123");
    const db = devContext.database();
    
    await assertFails(db.ref("devices/device456/state").set("ACTIVE"));
    await assertFails(db.ref("devices/device456/state").get());
  });

  it("allows admin to read device node if linked", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.database().ref("devices/device123/admins/admin999").set(true);
    });

    const adminContext = testEnv.authenticatedContext("admin999");
    const db = adminContext.database();

    await assertSucceeds(db.ref("devices/device123/state").get());
    await assertSucceeds(db.ref("devices/device123/commands/cmd1").set({ type: "LOCK" }));
  });

  it("denies admin to write device state directly", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.database().ref("devices/device123/admins/admin999").set(true);
    });

    const adminContext = testEnv.authenticatedContext("admin999");
    const db = adminContext.database();

    await assertFails(db.ref("devices/device123/state").set("LOCKED")); // Admins can't overwrite device's self-reported state
  });
});
