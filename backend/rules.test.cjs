const { before, after, beforeEach, test } = require('node:test');
const fs = require('node:fs');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, writeBatch, deleteDoc } = require('firebase/firestore');
let env;
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-campusbite', firestore: { rules: fs.readFileSync('../firestore.rules', 'utf8') } });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), 'slots/slot'), { stallId: 'stall', label: 'Tomorrow', startsAt: Date.now() + 86400000, capacity: 2, bookedCount: 0 });
  });
});
function order(uid = 'alice') { return { userId: uid, stallId: 'stall', slotId: 'slot', pickupWindow: 'Tomorrow', itemsJson: '[]', totalAmount: 35000, status: 'QUEUED', createdAt: Date.now(), updatedAt: Date.now() }; }
function reserve(db, id, uid = 'alice', booked = 1) {
  const batch = writeBatch(db);
  batch.set(doc(db, `orders/${id}`), order(uid));
  batch.update(doc(db, 'slots/slot'), { bookedCount: booked, lastOrderId: id });
  return batch.commit();
}
test('an order and its slot reservation must be atomic', async () => {
  const db = env.authenticatedContext('alice').firestore();
  await assertFails(setDoc(doc(db, 'orders/one'), order()));
  await assertSucceeds(reserve(db, 'one'));
});
test('users cannot read or change another user order', async () => {
  await reserve(env.authenticatedContext('alice').firestore(), 'one');
  const other = env.authenticatedContext('bob').firestore();
  await assertFails(getDoc(doc(other, 'orders/one')));
  await assertFails(updateDoc(doc(other, 'orders/one'), { status: 'READY' }));
});
test('cancellation must release exactly one place', async () => {
  const db = env.authenticatedContext('alice').firestore();
  await reserve(db, 'one');
  await assertFails(updateDoc(doc(db, 'orders/one'), { status: 'CANCELLED' }));
  const batch = writeBatch(db);
  batch.update(doc(db, 'orders/one'), { status: 'CANCELLED', updatedAt: Date.now() });
  batch.update(doc(db, 'slots/slot'), { bookedCount: 0, lastOrderId: 'one' });
  await assertSucceeds(batch.commit());
  await assertSucceeds(deleteDoc(doc(db, 'orders/one')));
});
test('users cannot advance vendor status or mutate capacity directly', async () => {
  const db = env.authenticatedContext('alice').firestore();
  await reserve(db, 'one');
  await assertFails(updateDoc(doc(db, 'orders/one'), { status: 'READY' }));
  await assertFails(updateDoc(doc(db, 'slots/slot'), { capacity: 100 }));
  await assertFails(updateDoc(doc(db, 'slots/slot'), { bookedCount: 2 }));
});
test('capacity cannot be exceeded', async () => {
  const db = env.authenticatedContext('alice').firestore();
  await assertSucceeds(reserve(db, 'one', 'alice', 1));
  await assertSucceeds(reserve(db, 'two', 'alice', 2));
  await assertFails(reserve(db, 'three', 'alice', 3));
});
test('only one review per owner and dish; ratings are constrained', async () => {
  const db = env.authenticatedContext('alice').firestore();
  const review = { userId: 'alice', dishId: 'dish', authorName: 'Alice', rating: 5, comment: 'Good', isDeleted: false, createdAt: 1, updatedAt: 1 };
  await assertSucceeds(setDoc(doc(db, 'reviews/alice-dish'), review));
  await assertFails(setDoc(doc(db, 'reviews/duplicate'), review));
  await assertFails(updateDoc(doc(db, 'reviews/alice-dish'), { rating: 6 }));
  await assertFails(updateDoc(doc(env.authenticatedContext('bob').firestore(), 'reviews/alice-dish'), { comment: 'Changed' }));
  await assertSucceeds(updateDoc(doc(db, 'reviews/alice-dish'), { isDeleted: true, updatedAt: 2 }));
});
test('anonymous clients cannot read menu-adjacent private data', async () => {
  const db = env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(db, 'slots/slot')));
});
