// Firestore rules tests for Olive (run by .github/workflows/rules-test.yml)
import { readFileSync } from "node:fs";
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import {
  doc, getDoc, setDoc, updateDoc, deleteDoc, writeBatch, serverTimestamp, arrayUnion, arrayRemove,
  increment, deleteField, collection, query, where, getDocs, addDoc,
} from "firebase/firestore";

const env = await initializeTestEnvironment({
  projectId: "demo-olive",
  firestore: { rules: readFileSync("../firestore.rules", "utf8"), host: "127.0.0.1", port: 8080 },
});

let failed = 0, passed = 0;
async function t(name, fn) {
  try { await fn(); passed++; console.log("  ✓", name); }
  catch (e) { failed++; console.log("  ✗", name, "→", e.message?.split("\n")[0]); }
}
const db = (uid) => env.authenticatedContext(uid).firestore();

async function seed() {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const f = ctx.firestore();
    await setDoc(doc(f, "users/alice"), { uid: "alice", displayName: "Alice", friendIds: ["bob"], friendCount: 1 });
    await setDoc(doc(f, "users/bob"), { uid: "bob", displayName: "Bob", friendIds: ["alice"], friendCount: 1 });
    await setDoc(doc(f, "users/eve"), { uid: "eve", displayName: "Eve", friendIds: [], friendCount: 0 });
    await setDoc(doc(f, "users/carol"), { uid: "carol", displayName: "Carol", friendIds: [], friendCount: 0 });
    await setDoc(doc(f, "friendRequests/r1"), { fromUid: "carol", toUid: "alice", status: "PENDING", fromName: "Carol" });
    await setDoc(doc(f, "presence/alice"), { nowPlaying: { title: "x" } });
    await setDoc(doc(f, "sessions/alice"), {
      hostUid: "alice", hostName: "Alice", active: true, invited: ["bob"], members: ["alice"],
      memberNames: { alice: "Alice" }, openControl: true, startedAt: 1, updatedAt: 1,
    });
  });
}

console.log("Sessions");
await seed();
await t("host reads own session", () => assertSucceeds(getDoc(doc(db("alice"), "sessions/alice"))));
await t("invited friend reads session", () => assertSucceeds(getDoc(doc(db("bob"), "sessions/alice"))));
await t("stranger can't read session", () => assertFails(getDoc(doc(db("eve"), "sessions/alice"))));
await t("invite query works", () => assertSucceeds(getDocs(query(collection(db("bob"), "sessions"), where("invited", "array-contains", "bob")))));
await t("host can't invite a non-friend", () => assertFails(updateDoc(doc(db("alice"), "sessions/alice"), { invited: arrayUnion("eve") })));
await t("host heartbeat update ok", () => assertSucceeds(updateDoc(doc(db("alice"), "sessions/alice"), { isPlaying: true, positionMs: 5, updatedAt: 2 })));
await t("new session: only friends invited", () => assertFails(setDoc(doc(db("bob"), "sessions/bob"), {
  hostUid: "bob", hostName: "Bob", active: true, invited: ["eve"], members: ["bob"], memberNames: { bob: "Bob" } })));
await t("new session with friend ok", () => assertSucceeds(setDoc(doc(db("bob"), "sessions/bob"), {
  hostUid: "bob", hostName: "Bob", active: true, invited: ["alice"], members: ["bob"], memberNames: { bob: "Bob" } })));
await t("stranger can't join", () => assertFails(updateDoc(doc(db("eve"), "sessions/alice"), { members: arrayUnion("eve"), "memberNames.eve": "Eve" })));
await t("guest can't join under a fake name", () => assertFails(updateDoc(doc(db("bob"), "sessions/alice"), { members: arrayUnion("bob"), "memberNames.bob": "Alice" })));
await t("guest joins", () => assertSucceeds(updateDoc(doc(db("bob"), "sessions/alice"), { members: arrayUnion("bob"), "memberNames.bob": "Bob" })));
await t("guest can't remove the host", () => assertFails(updateDoc(doc(db("bob"), "sessions/alice"), { members: arrayRemove("alice") })));
await t("guest can't rename others", () => assertFails(updateDoc(doc(db("bob"), "sessions/alice"), { "memberNames.alice": "Loser" })));
await t("guest can't change track", () => assertFails(updateDoc(doc(db("bob"), "sessions/alice"), { track: { title: "x", fileUrl: "/data/data/x" } })));

console.log("Commands");
const cmd = (extra = {}) => ({ command: { id: "c" + Math.random(), type: "pause", by: "bob", byName: "Bob", at: Date.now(), sentAt: serverTimestamp(), ...extra } });
await t("guest command ok", () => assertSucceeds(updateDoc(doc(db("bob"), "sessions/alice"), cmd())));
await t("command spam blocked", () => assertFails(updateDoc(doc(db("bob"), "sessions/alice"), cmd())));
await new Promise((r) => setTimeout(r, 700));
await t("fake byName blocked", () => assertFails(updateDoc(doc(db("bob"), "sessions/alice"), cmd({ byName: "Alice" }))));
await t("unknown command type blocked", () => assertFails(updateDoc(doc(db("bob"), "sessions/alice"), cmd({ type: "upload" }))));
await t("command as someone else blocked", () => assertFails(updateDoc(doc(db("bob"), "sessions/alice"), cmd({ by: "alice" }))));

console.log("Chat");
const msg = (extra = {}) => ({ uid: "bob", name: "Bob", text: "hi", at: Date.now(), ...extra });
await t("member sends chat", () => assertSucceeds(addDoc(collection(db("bob"), "sessions/alice/messages"), msg())));
await t("chat under fake name blocked", () => assertFails(addDoc(collection(db("bob"), "sessions/alice/messages"), msg({ name: "Alice" }))));
await t("long chat blocked", () => assertFails(addDoc(collection(db("bob"), "sessions/alice/messages"), msg({ text: "x".repeat(301) }))));
await t("future-dated chat blocked", () => assertFails(addDoc(collection(db("bob"), "sessions/alice/messages"), msg({ at: Date.now() + 86_400_000 }))));
await t("stranger can't read chat", () => assertFails(getDocs(collection(db("eve"), "sessions/alice/messages"))));
await t("stranger can't post chat", () => assertFails(addDoc(collection(db("eve"), "sessions/alice/messages"), msg({ uid: "eve", name: "Eve" }))));
await t("member reads chat", () => assertSucceeds(getDocs(collection(db("bob"), "sessions/alice/messages"))));

console.log("Inbox");
const notif = (from, name, extra = {}) => ({ notifId: "n", type: "SONG_SHARED", fromUid: from, fromName: name, sentAt: 1, isRead: false, ...extra });
await t("friend shares a song", () => assertSucceeds(setDoc(doc(db("bob"), "users/alice/inbox/n1"), notif("bob", "Bob"))));
await t("stranger can't share / invite", () => assertFails(setDoc(doc(db("eve"), "users/alice/inbox/n2"), notif("eve", "Eve", { type: "LISTEN_INVITE" }))));
await t("friend can't fake their name", () => assertFails(setDoc(doc(db("bob"), "users/alice/inbox/n3"), notif("bob", "Mom"))));
await t("friend request (with real request) ok", async () => {
  const f = db("eve"); const b = writeBatch(f);
  b.set(doc(f, "friendRequests/r9"), { fromUid: "eve", toUid: "alice", status: "PENDING", fromName: "Eve" });
  b.set(doc(f, "users/alice/inbox/n4"), notif("eve", "Eve", { type: "FRIEND_REQUEST", requestId: "r9" }));
  await assertSucceeds(b.commit());
});
await t("fake friend request blocked", () => assertFails(setDoc(doc(db("eve"), "users/bob/inbox/n5"), notif("eve", "Eve", { type: "FRIEND_REQUEST", requestId: "r1" }))));

console.log("Friends");
await t("can't add yourself to someone's friends", () => assertFails(updateDoc(doc(db("eve"), "users/bob"), { friendIds: arrayUnion("eve"), friendCount: increment(1) })));
await t("accept request (both lists) ok", async () => {
  const f = db("alice"); const b = writeBatch(f);
  b.update(doc(f, "friendRequests/r1"), { status: "ACCEPTED", respondedAt: 1 });
  b.set(doc(f, "users/alice"), { friendIds: arrayUnion("carol"), friendCount: increment(1) }, { merge: true });
  b.update(doc(f, "users/carol"), { friendIds: arrayUnion("alice"), friendCount: increment(1), lastAcceptedRequest: "r1" });
  b.set(doc(f, "users/carol/inbox/n6"), notif("alice", "Alice", { type: "FRIEND_ACCEPTED", requestId: "r1" }));
  await assertSucceeds(b.commit());
});
await t("reuse an accepted request on someone else blocked", () => assertFails(updateDoc(doc(db("alice"), "users/eve"), { friendIds: arrayUnion("alice"), friendCount: increment(1), lastAcceptedRequest: "r1" })));
await t("remove yourself from a friend's list ok", () => assertSucceeds(updateDoc(doc(db("bob"), "users/alice"), { friendIds: arrayRemove("bob"), friendCount: increment(-1) })));
await t("stranger can't read others' requests", () => assertFails(getDoc(doc(db("eve"), "friendRequests/r1"))));
await t("my incoming requests query ok", () => assertSucceeds(getDocs(query(collection(db("alice"), "friendRequests"), where("toUid", "==", "alice"), where("status", "==", "PENDING")))));
await t("my outgoing requests query ok", () => assertSucceeds(getDocs(query(collection(db("eve"), "friendRequests"), where("fromUid", "==", "eve"), where("status", "==", "PENDING")))));
await t("sender can't mark own request accepted", () => assertFails(updateDoc(doc(db("eve"), "friendRequests/r9"), { status: "ACCEPTED" })));

console.log("Presence & transfers");
await seed();
await t("friend sees presence", () => assertSucceeds(getDoc(doc(db("bob"), "presence/alice"))));
await t("stranger can't see presence", () => assertFails(getDoc(doc(db("eve"), "presence/alice"))));
await t("transfer to non-friend blocked", () => assertFails(setDoc(doc(db("alice"), "transfers/t1"), { fromUid: "alice", url: "u", pending: ["eve"], recipients: ["eve"] })));
await t("transfer to friend ok", () => assertSucceeds(setDoc(doc(db("alice"), "transfers/t2"), { fromUid: "alice", url: "u", pending: ["bob"], recipients: ["bob"] })));
await t("receiver removes self", () => assertSucceeds(updateDoc(doc(db("bob"), "transfers/t2"), { pending: arrayRemove("bob") })));
await t("receiver can still read it", () => assertSucceeds(getDoc(doc(db("bob"), "transfers/t2"))));
await t("host ends session (delete)", () => assertSucceeds(deleteDoc(doc(db("alice"), "sessions/alice"))));

await env.cleanup();
console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
