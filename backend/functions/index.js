const functions = require('firebase-functions/v1');
const admin = require('firebase-admin');
admin.initializeApp();

// Deploy alongside the app so deleting a Firebase account removes server records.
exports.removeAccountData = functions.auth.user().onDelete(async (user) => {
  const db = admin.firestore();
  const orders = await db.collection('orders').where('userId', '==', user.uid).get();
  for (const document of orders.docs) {
    await db.runTransaction(async transaction => {
      const fresh = await transaction.get(document.ref);
      if (!fresh.exists) return;
      const order = fresh.data();
      const slotRef = db.collection('slots').doc(order.slotId);
      const slot = await transaction.get(slotRef);
      if (slot.exists && ['QUEUED', 'CONFIRMED', 'PREPARING', 'READY'].includes(order.status)) {
        transaction.update(slotRef, { bookedCount: Math.max(0, slot.data().bookedCount - 1) });
      }
      transaction.delete(document.ref);
    });
  }
  const reviews = await db.collection('reviews').where('userId', '==', user.uid).get();
  const writer = db.bulkWriter();
  reviews.docs.forEach(document => writer.delete(document.ref));
  await writer.close();
});
