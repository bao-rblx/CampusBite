// Run with Application Default Credentials for YOUR Firebase project.
// Usage: node seed-slots.cjs PROJECT_ID 2026-11-03 STALL_ID [STALL_ID ...]
const admin = require('firebase-admin');
const [projectId, date, ...stalls] = process.argv.slice(2);
if (!projectId || !/^\d{4}-\d{2}-\d{2}$/.test(date || '') || !stalls.length) {
  throw new Error('Usage: node seed-slots.cjs PROJECT_ID YYYY-MM-DD STALL_ID [STALL_ID ...]');
}
admin.initializeApp({ projectId });
const db = admin.firestore();
(async () => {
  for (const stallId of stalls) {
    for (let index = 0; index < 8; index++) {
      const startsAt = new Date(`${date}T11:30:00+07:00`).getTime() + index * 15 * 60000;
      const time = value => new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Ho_Chi_Minh' }).format(value);
      const ref = db.collection('slots').doc(`${stallId}-${date}-${index}`);
      await db.runTransaction(async transaction => {
        if ((await transaction.get(ref)).exists) return; // Never reset existing reservations.
        transaction.set(ref, { stallId, label: `${date} · ${time(startsAt)}–${time(startsAt + 15 * 60000)}`, startsAt, capacity: 20, bookedCount: 0 });
      });
    }
  }
  console.log('Pickup slots created; existing slots preserved.');
})().catch(error => { console.error(error); process.exitCode = 1; });
