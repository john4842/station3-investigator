const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(process.argv[2] || path.join(__dirname, '../src/main/resources/static/app.js'), 'utf8');
const nodes = {};
function node(id) {
  return nodes[id] ??= { disabled: false, hidden: false, textContent: '', handlers: {},
    classList: { add() {}, remove() {}, toggle() {} },
    addEventListener(type, fn) { this.handlers[type] = fn; },
    querySelector() { return node(id + '/child'); }, click() {} };
}
const context = { document: { querySelector: node }, fetch: async () => ({ok:true,json:async()=>({capabilities:{canonicalAvailable:true,uploadAvailable:true,cameraEvidenceAvailable:true}})}),
  TypeError, Error, Number, Date, String, FormData: class {} };
vm.createContext(context);
vm.runInContext(source, context);
const evaluate = text => vm.runInContext(text, context);
(async () => {
  await new Promise(resolve => setImmediate(resolve));
  // A previously successful report must not survive either network-failure path.
  for (const selector of ['#canonical-submit', '#analysis-form']) {
    evaluate('latestResult = {old:true}; results.hidden = false');
    context.fetch = async () => { throw new TypeError('Failed to fetch'); };
    const handler = node(selector).handlers[selector === '#analysis-form' ? 'submit' : 'click'];
    await handler({preventDefault(){}});
    assert.equal(evaluate('latestResult'), null);
    assert.equal(node('#results').hidden, true);
    assert.match(node('#message').textContent, /Cannot reach/);
    assert.equal(node('#canonical-submit').disabled, false);
    assert.equal(node('#upload-submit').disabled, false);
  }
  // Server errors and malformed responses clear old downloads too.
  for (const response of [{ok:false,json:async()=>({message:'Invalid archive'})},
                          {ok:true,json:async()=>{throw new SyntaxError('Invalid JSON');}}]) {
    evaluate('latestResult = {old:true}');
    await context.handleResponse(response);
    assert.equal(evaluate('latestResult'), null);
    assert.equal(node('#results').hidden, true);
  }
  // Rendering must succeed before an export becomes available.
  evaluate('render = () => { throw new Error("Missing result fields"); }');
  await context.handleResponse({ok:true,json:async()=>({})});
  assert.equal(evaluate('latestResult'), null);
  evaluate('render = () => { results.hidden = false; }');
  const payload = {measurementRows:30,assessmentCount:10,provenanceStatus:'VERIFIED_CANONICAL_SOURCE'};
  context.fetch = async () => ({ok:true,json:async()=>payload});
  await node('#canonical-submit').handlers.click();
  assert.equal(evaluate('latestResult'), payload);
  assert.equal(node('#results').hidden, false);
  assert.match(node('#message').textContent, /Done/);
  // Public mode must neither fetch restricted analysis nor expose stale results.
  context.fetch = async () => ({ok:true,json:async()=>({capabilities:{
    canonicalAvailable:false,uploadAvailable:false,cameraEvidenceAvailable:false}})});
  await context.initialize();
  assert.equal(node('#canonical-submit').disabled, true);
  assert.equal(node('#upload-submit').disabled, true);
  assert.match(node('#availability-message').textContent, /not installed/);
  let forbiddenCalls = 0;
  context.fetch = async () => { forbiddenCalls++; throw new Error('Unexpected restricted request'); };
  await node('#canonical-submit').handlers.click();
  await node('#analysis-form').handlers.submit({preventDefault(){}});
  assert.equal(forbiddenCalls, 0);
  const synthetic = {synthetic:true,measurementRows:6,assessmentCount:2,evidenceType:'SYNTHETIC_FIXTURE'};
  context.fetch = async () => ({ok:true,json:async()=>synthetic});
  await node('#synthetic-submit').handlers.click();
  assert.equal(evaluate('latestResult'), synthetic);
  assert.match(node('#message').textContent, /synthetic, not plant evidence/);
  assert.equal(node('#canonical-brief').hidden, true);
  assert.equal(node('#canonical-evidence').hidden, true);
  assert.equal(node('#synthetic-submit').disabled, false);
  console.log('PASS: public synthetic mode, disabled restricted requests, canonical/upload network failures, server error, malformed JSON, rendering failure, and successful retry');
})().catch(error => { console.error(error); process.exitCode = 1; });
