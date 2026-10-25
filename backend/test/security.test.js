import test from 'node:test';
import assert from 'node:assert/strict';
import {signature,canonical,hash,equal,httpsURL,escapeHTML} from '../src/security.js';
test('Payfast form encoding and ordered canonical signature',()=>{const p=new URLSearchParams([['merchant_id','10000100'],['item_name',"TV & movies"],['empty',''],['signature','ignored']]);assert.equal(canonical(p),'merchant_id=10000100&item_name=TV+%26+movies');assert.equal(signature(new URLSearchParams()),'d41d8cd98f00b204e9800998ecf8427e');assert.notEqual(signature(p),signature(p,'secret'));assert.equal(signature(p),signature(new URLSearchParams(p)));});
test('tokens stored as deterministic SHA256 hashes',()=>{assert.equal(hash('hello'),'2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824');assert.notEqual(hash('hello'),hash('Hello'));});
test('signature equality rejects missing and partial values',()=>{assert.equal(equal('abc','abc'),true);for(const x of [null,undefined,'ab','abd'])assert.equal(equal('abc',x),false);});
test('content URLs reject script, cleartext and embedded credentials',()=>{assert.equal(httpsURL('https://youtube.com/watch?v=1'),true);for(const s of ['javascript:alert(1)','http://example.com','https://admin:password@example.com','garbage'])assert.equal(httpsURL(s),false);});
test('checkout escapes untrusted form fields',()=>{assert.equal(escapeHTML('<script>"&\''),'&lt;script&gt;&quot;&amp;&#39;');});
