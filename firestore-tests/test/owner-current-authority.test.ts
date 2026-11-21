import {afterAll,beforeAll,beforeEach,describe,it} from 'vitest';
import {assertFails,assertSucceeds,type RulesTestEnvironment} from '@firebase/rules-unit-testing';
import {doc,setDoc,updateDoc,getDoc} from 'firebase/firestore';
import {createTestEnv} from './setup';
let env:RulesTestEnvironment;
beforeAll(async()=>{env=await createTestEnv();});
beforeEach(async()=>{
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx=>{
    await ctx.firestore().doc('users/owner').set({role:'OWNER',ownerStatus:'APPROVED',accountStatus:'ACTIVE'});
    for(const status of ['ACTIVE','HIDDEN','LOCKED']) {
      await ctx.firestore().doc(`venues/${status}`).set({ownerId:'owner',status});
      await ctx.firestore().doc(`venues/${status}/courts/c1`).set({name:'TEST',surfaceType:'PU',sortOrder:0,isActive:true});
    }
  });
});
afterAll(async()=>{await env.cleanup();});
describe('Feature 11 fresh authority and atomic pricing boundary',()=>{
  it('cannot bypass the court/count transaction with direct writes',async()=>{
    const db=env.authenticatedContext('owner',{appRole:'OWNER'}).firestore();
    const valid={name:'TEST',surfaceType:'PU',sortOrder:1,isActive:true};
    for(const patch of [{name:''},{surfaceType:'INVALID'},{sortOrder:-1},{sortOrder:1.5},{isActive:'true'}]) {
      await assertFails(setDoc(doc(db,'venues/ACTIVE/courts/new'),{...valid,...patch}));
    }
    await assertFails(setDoc(doc(db,'venues/ACTIVE/courts/new'),valid));
    await assertFails(setDoc(doc(db,'venues/ACTIVE/courts/another'),valid));
  });
  it('denies creating and modifying existing courts under LOCKED parent',async()=>{
    const db=env.authenticatedContext('owner',{appRole:'OWNER'}).firestore();
    await assertFails(updateDoc(doc(db,'venues/LOCKED/courts/c1'),{name:'BYPASS'}));
    await assertFails(setDoc(doc(db,'venues/LOCKED/courts/new'),{name:'BYPASS',surfaceType:'PU',sortOrder:1,isActive:true}));
    for(const status of ['ACTIVE','HIDDEN']) await assertSucceeds(getDoc(doc(db,`venues/${status}/courts/c1`)));
  });
  for(const change of [{role:'PLAYER'},{accountStatus:'LOCKED'},{deletionRequestedAt:new Date()}]) {
    it(`rejects stale OWNER claim after ${JSON.stringify(change)}`,async()=>{
      const db=env.authenticatedContext('owner',{appRole:'OWNER'}).firestore();
      await env.withSecurityRulesDisabled(ctx=>ctx.firestore().doc('users/owner').update(change));
      await assertFails(updateDoc(doc(db,'venues/ACTIVE'),{name:'BYPASS'}));
      await assertFails(updateDoc(doc(db,'venues/ACTIVE/courts/c1'),{name:'BYPASS'}));
    });
  }
  it('cannot bypass atomic rule and minimum-price validation by writing directly',async()=>{
    const db=env.authenticatedContext('owner',{appRole:'OWNER'}).firestore();
    await assertFails(setDoc(doc(db,'venues/ACTIVE/priceRules/r1'),{daysOfWeek:[1],startTime:'0800',endTime:'0900',pricePerHour:60000,label:'NORMAL'}));
  });
  it('courtCount is server-managed even for a client with ADMIN claims',async()=>{
    for(const role of ['OWNER','ADMIN']) {
      const db=env.authenticatedContext('owner',{appRole:role}).firestore();
      await assertFails(updateDoc(doc(db,'venues/ACTIVE'),{courtCount:99}));
    }
  });
});
