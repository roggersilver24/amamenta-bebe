import { before, after, beforeEach, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, setDoc, updateDoc, deleteDoc, getDoc, getDocs, collection, query, where, writeBatch, serverTimestamp, Timestamp } from 'firebase/firestore';

let env;
const token = 'a'.repeat(64);
const expired = 'b'.repeat(64);
const revoked = 'c'.repeat(64);
const user = (uid, email = `${uid}@example.com`, verified = true) => env.authenticatedContext(uid, { email, email_verified: verified }).firestore();
const feedingPayload = () => ({ timeMillis: 1700000000000, type: 'BREAST', side: 'LEFT', amountMl: null, note: '', leftDurationMillis: 1000, rightDurationMillis: 0 });
const record = uid => ({ kind: 'FEEDING', payload: feedingPayload(), authorUid: uid, authorName: uid, modifiedUid: uid, revision: 1, deleted: false, updatedAt: serverTimestamp() });
before(async () => { env = await initializeTestEnvironment({ projectId: 'demo-amamenta-bebe', firestore: { rules: readFileSync(new URL('./firestore.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: 8080 } }); });
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    await setDoc(doc(db, 'families/f1'), { ownerUid: 'admin', name: 'Família', createdAt: Timestamp.now() });
    await setDoc(doc(db, 'families/f1/members/admin'), { role: 'ADMIN', name: 'Admin', invitationId: '' });
    await setDoc(doc(db, 'families/f1/members/caregiver'), { role: 'CAREGIVER', name: 'Cuidador', invitationId: 'seed' });
    await setDoc(doc(db, 'families/f1/babies/b1'), { name: 'Bebê', createdAt: Timestamp.now() });
    await setDoc(doc(db, 'families/f1/babies/b1/records/r1'), { ...record('admin'), updatedAt: Timestamp.now() });
    await setDoc(doc(db, `invitations/${token}`), { familyId: 'f1', createdBy: 'admin', targetEmail: 'guest@example.com', expiresAt: Timestamp.fromMillis(Date.now() + 3600000), consumedBy: '' });
    await setDoc(doc(db, `invitations/${expired}`), { familyId: 'f1', createdBy: 'admin', targetEmail: 'guest@example.com', expiresAt: Timestamp.fromMillis(Date.now() - 60000), consumedBy: '' });
  });
});
function acceptance(db, iid = token, role = 'CAREGIVER') {
  const batch = writeBatch(db);
  batch.update(doc(db, `invitations/${iid}`), { consumedBy: 'guest' });
  batch.set(doc(db, 'families/f1/members/guest'), { role, name: 'Visitante', invitationId: iid });
  return batch.commit();
}
test('owner bootstraps family and admin atomically; standalone family denied', async () => {
  const db = user('newowner');
  await assertFails(setDoc(doc(db, 'families/orphan'), { ownerUid: 'newowner', name: 'Família', createdAt: serverTimestamp() }));
  const batch = writeBatch(db);
  batch.set(doc(db, 'families/new'), { ownerUid: 'newowner', name: 'Família', createdAt: serverTimestamp() });
  batch.set(doc(db, 'families/new/members/newowner'), { role: 'ADMIN', name: 'Dono', invitationId: '' });
  batch.set(doc(db, 'families/new/babies/b1'), { name: 'Bebê', createdAt: serverTimestamp() });
  await assertSucceeds(batch.commit());
});
test('unauthenticated and cross-family access is denied', async () => {
  for (const db of [env.unauthenticatedContext().firestore(), user('outsider')]) {
    await assertFails(getDoc(doc(db, 'families/f1')));
    await assertFails(getDocs(collection(db, 'families/f1/babies/b1/records')));
    await assertFails(setDoc(doc(db, 'families/f1/babies/b1/records/new'), record('outsider')));
  }
  await assertSucceeds(getDocs(collection(user('caregiver'), 'families/f1/babies/b1/records')));
});
test('removed member loses reads and writes; admin cannot be removed', async () => {
  const admin = user('admin');
  await assertFails(deleteDoc(doc(admin, 'families/f1/members/admin')));
  await assertSucceeds(deleteDoc(doc(admin, 'families/f1/members/caregiver')));
  await assertFails(getDoc(doc(user('caregiver'), 'families/f1/babies/b1/records/r1')));
  await assertFails(setDoc(doc(user('caregiver'), 'families/f1/babies/b1/records/new'), record('caregiver')));
});
test('caregiver cannot escalate role or change owner', async () => {
  const db = user('caregiver');
  await assertFails(updateDoc(doc(db, 'families/f1/members/caregiver'), { role: 'ADMIN' }));
  await assertFails(updateDoc(doc(db, 'families/f1'), { ownerUid: 'caregiver' }));
  await assertFails(setDoc(doc(db, 'families/f1/members/outsider'), { role: 'ADMIN', name: 'Outro', invitationId: '' }));
});
test('records preserve author, enforce monotonic revision, timestamp, bounded payload and tombstones', async () => {
  const db = user('caregiver');
  const ref = doc(db, 'families/f1/babies/b1/records/r1');
  await assertFails(updateDoc(ref, { authorUid: 'caregiver', modifiedUid: 'caregiver', revision: 2, updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(ref, { modifiedUid: 'caregiver', revision: 3, updatedAt: serverTimestamp() }));
  await assertFails(setDoc(doc(db, 'families/f1/babies/b1/records/huge'), { ...record('caregiver'), payload: { ...feedingPayload(), note: 'x'.repeat(5001) } }));
  await assertFails(setDoc(doc(db, 'families/f1/babies/b1/records/forged'), record('admin')));
  await assertFails(setDoc(doc(db, 'families/f1/babies/b1/records/clock'), { ...record('caregiver'), updatedAt: Timestamp.fromMillis(1) }));
  await assertFails(setDoc(doc(db, 'families/f1/babies/b1/records/extra'), { ...record('caregiver'), familyId: 'other' }));
  await assertFails(setDoc(doc(db, 'families/f1/babies/b1/records/kind'), { ...record('caregiver'), kind: 'INVALID' }));
  await assertSucceeds(updateDoc(ref, { modifiedUid: 'caregiver', revision: 2, deleted: true, updatedAt: serverTimestamp() }));
  await assertFails(deleteDoc(ref));
});
test('typed feeding payload rejects malformed, missing, extra and out-of-range fields', async () => {
  const db = user('caregiver');
  const ref = doc(db, 'families/f1/babies/b1/records/invalid');
  const missing = feedingPayload(); delete missing.amountMl;
  const invalidPayloads = ['{}', null, [], missing, { ...feedingPayload(), extra: true }];
  for (const [key, values] of Object.entries({
    timeMillis: [0, -1, 9000000000000001, 1.5, '1700000000000'],
    type: ['UNKNOWN', 1, null], side: ['UNKNOWN', false], amountMl: [-1, 10000, 1.5, '20'],
    note: [null, 1, 'x'.repeat(5001)], leftDurationMillis: [-1, 9000000000000001, 1.5, '0'],
    rightDurationMillis: [-1, 9000000000000001, 1.5, null]
  })) for (const value of values) invalidPayloads.push({ ...feedingPayload(), [key]: value });
  for (const payload of invalidPayloads) await assertFails(setDoc(ref, { ...record('caregiver'), payload }));
  for (const revision of [0, -1, 2, 1.5, '1', 9000000000000001])
    await assertFails(setDoc(ref, { ...record('caregiver'), revision }));
  await assertSucceeds(setDoc(ref, { ...record('caregiver'), payload: { ...feedingPayload(), timeMillis: 9000000000000000, amountMl: 9999, note: 'x'.repeat(5000), leftDurationMillis: 9000000000000000, rightDurationMillis: 9000000000000000 } }));
});
test('typed diaper payload permits combined one-change event and rejects malformed values', async () => {
  const db = user('caregiver');
  const ref = doc(db, 'families/f1/babies/b1/records/diaper');
  for (const payload of [{ timeMillis: 0, type: 'WET' }, { timeMillis: 9000000000000001, type: 'WET' },
    { timeMillis: 1.5, type: 'WET' }, { timeMillis: '1', type: 'WET' }, { timeMillis: 1, type: 'UNKNOWN' },
    { timeMillis: 1 }, { timeMillis: 1, type: 'BOTH', extra: true }])
    await assertFails(setDoc(ref, { ...record('caregiver'), kind: 'DIAPER', payload }));
  for (const type of ['WET','DIRTY','BOTH'])
    await assertSucceeds(setDoc(doc(db, `families/f1/babies/b1/records/${type}`), { ...record('caregiver'), kind: 'DIAPER', payload: { timeMillis: 1700000000000, type } }));
});
test('orphan membership cannot authorize writes without family or baby parents', async () => {
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), 'families/orphan/members/caregiver'), { role: 'CAREGIVER', name: 'Caregiver', invitationId: token });
  });
  await assertFails(getDoc(doc(user('caregiver'), 'families/orphan')));
  await assertFails(setDoc(doc(user('caregiver'), 'families/orphan/babies/b1/records/r1'), record('caregiver')));
  await assertFails(setDoc(doc(user('caregiver'), 'families/f1/babies/missing/records/r1'), record('caregiver')));
});
test('verified intended recipient accepts exactly once in atomic batch', async () => {
  const db = user('guest');
  await assertSucceeds(getDoc(doc(db, `invitations/${token}`)));
  await assertSucceeds(acceptance(db));
  await assertSucceeds(getDocs(collection(db, 'families/f1/babies/b1/records')));
  await assertFails(acceptance(db));
});
test('invites reject expiry, wrong email, unverified email, escalation and missing member transaction', async () => {
  await assertFails(acceptance(user('guest'), expired));
  await assertFails(acceptance(user('guest', 'wrong@example.com')));
  await assertFails(acceptance(user('guest', 'guest@example.com', false)));
  await assertFails(acceptance(user('guest'), token, 'ADMIN'));
  await assertFails(updateDoc(doc(user('guest'), `invitations/${token}`), { consumedBy: 'guest' }));
  await assertFails(setDoc(doc(user('guest'), 'families/f1/members/guest'), { role: 'CAREGIVER', name: 'Guest', invitationId: token }));
});
test('admin revokes invitation and only scoped invitation query is permitted', async () => {
  const admin = user('admin');
  await assertSucceeds(getDocs(query(collection(admin, 'invitations'), where('familyId', '==', 'f1'))));
  await assertFails(getDocs(collection(admin, 'invitations')));
  await assertFails(getDocs(collection(user('guest'), 'invitations')));
  await assertSucceeds(deleteDoc(doc(admin, `invitations/${token}`)));
  await assertFails(acceptance(user('guest')));
});
test('invitation creation is admin only and expires within 24 hours', async () => {
  const data = { familyId: 'f1', createdBy: 'admin', targetEmail: 'guest@example.com', expiresAt: Timestamp.fromMillis(Date.now() + 3600000), consumedBy: '' };
  await assertSucceeds(setDoc(doc(user('admin'), `invitations/${revoked}`), data));
  await assertFails(setDoc(doc(user('caregiver'), `invitations/${'d'.repeat(64)}`), { ...data, createdBy: 'caregiver' }));
  await assertFails(setDoc(doc(user('admin'), `invitations/${'e'.repeat(64)}`), { ...data, expiresAt: Timestamp.fromMillis(Date.now() + 172800000) }));
  await assertFails(setDoc(doc(user('admin'), 'invitations/short-token'), data));
  await assertFails(setDoc(doc(user('admin'), `invitations/${'f'.repeat(64)}`), { ...data, targetEmail: 'Guest@Example.com' }));
});
test('invitation and member envelopes reject missing, extra, wrong types and oversized fields', async () => {
  const admin = user('admin');
  const data = { familyId: 'f1', createdBy: 'admin', targetEmail: 'guest@example.com', expiresAt: Timestamp.fromMillis(Date.now() + 3600000), consumedBy: '' };
  const missing = { ...data }; delete missing.targetEmail;
  for (const invalid of [missing, { ...data, extra: true }, { ...data, targetEmail: 1 },
    { ...data, targetEmail: 'a'.repeat(255) }, { ...data, expiresAt: 1 }, { ...data, consumedBy: false }])
    await assertFails(setDoc(doc(admin, `invitations/${'f'.repeat(64)}`), invalid));
  const db = user('newowner');
  for (const invalid of [{ role: 'INVALID', name: 'Dono', invitationId: '' },
    { role: 'ADMIN', name: 'Dono' }, { role: 'ADMIN', name: 1, invitationId: '' },
    { role: 'ADMIN', name: 'x'.repeat(101), invitationId: '' },
    { role: 'ADMIN', name: 'Dono', invitationId: 'x'.repeat(65) },
    { role: 'ADMIN', name: 'Dono', invitationId: '', extra: true }]) {
    const batch = writeBatch(db);
    batch.set(doc(db, 'families/new'), { ownerUid: 'newowner', name: 'Família', createdAt: serverTimestamp() });
    batch.set(doc(db, 'families/new/members/newowner'), invalid);
    await assertFails(batch.commit());
  }
});
