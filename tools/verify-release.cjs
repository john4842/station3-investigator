const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const base = process.argv[2];
const output = process.argv[3];
if (!/^http:\/\/127\.0\.0\.1:\d+$/.test(base || '') || !output) throw Error('usage: node tools/verify-release.cjs http://127.0.0.1:PORT EXISTING_OUTPUT_DIR');
async function get(path) { const r = await fetch(base + path); assert.equal(r.status, 200, path); return r.text(); }
(async () => {
 const home = await get('/');
 assert.ok(home.includes('/evaluation.html'));
 for (const path of ['/styles.css','/canonical.css','/runs.css','/executive.css','/evidence.css','/recipe7-evidence.svg','/run5-tank-context.svg','/app.js','/executive.js','/runs.js','/evaluation.html','/evaluation.js']) await get(path);
 const response = await fetch(base+'/api/analyze/canonical',{method:'POST'}); assert.equal(response.status,200);
 const payload = await response.json();
 assert.equal(payload.provenanceStatus,'VERIFIED_CANONICAL_SOURCE');
 assert.equal(payload.measurementRows,30); assert.equal(payload.assessmentCount,10);
 assert.equal(payload.extractionReceipt.sourceFrameContract.frameCount,845);
 assert.equal(payload.extractionReceipt.sourceFrameContract.inFillFrames,509);
 assert.equal(payload.extractionReceipt.sourceFrameContract.betweenFillFrames,336);
 assert.equal(payload.processContext.crossCamera.views.length,3);
 assert.equal(payload.processContext.crossCamera.joinStatus,'EXACT_INDEX_TIMESTAMP');
 assert.equal(new Set(payload.processContext.crossCamera.views.map(v=>v.timestamp)).size,1);
 assert.equal(payload.processContext.timingModel.perTankVolumesAndActiveComponentCountR2,0.925);
 assert.equal(payload.processContext.timingModel.totalVolumeOnlyR2,0.264);
 const seven = payload.assessments.find(a=>a.recipeId===7);
 assert.equal(seven.withinBatchMetrics.vial1ToVial3DeltaE00.toFixed(3),'13.525');
 assert.equal(seven.laterPairDeltaE00.toFixed(3),'0.798');
 const e = JSON.parse(await get('/api/evaluation'));
 assert.equal(e.denominator,10); assert.equal(e.changedDecisions,5); assert.equal(e.accuracy,'NOT_ESTIMABLE');
 assert.equal(e.rows.filter(r=>r.guarded==='ABSTAIN').length,4);
 assert.equal(e.rows.filter(r=>r.guarded==='REVIEW').length,1);
 const code = await get('/evaluation.js');
 function node(){return {textContent:'',children:[],appendChild(n){this.children.push(n)},replaceChildren(...n){this.children=n}};}
 for (const fail of [false,true]) {
   const nodes={};
   vm.runInNewContext(code,{document:{querySelector:k=>nodes[k]??=node(),createElement:node,createDocumentFragment:node},fetch:async()=>{if(fail)throw Error('offline');return {ok:true,json:async()=>e}},Number,String,Error});
   await new Promise(r=>setImmediate(r));
   if(fail) {assert.match(nodes['#evaluation-status'].textContent,/unavailable/);assert.equal(nodes['#evaluation-rows'].children.length,0);}
   else {assert.match(nodes['#evaluation-status'].textContent,/5 of 10/);assert.equal(nodes['#evaluation-rows'].children[0].children.length,10);}
 }
 const stub = ()=>({addEventListener(){},click(){},querySelector:stub,classList:{add(){},remove(){},toggle(){}}});
 const ctx={document:{querySelector:stub},fetch:async()=>({ok:true,json:async()=>({})}),Number,Date,String,Error};
 vm.createContext(ctx);vm.runInContext(await get('/app.js'),ctx);
 const report=ctx.htmlReport(payload);assert.ok(report.includes('13.525')&&report.includes('0.798'));
 fs.writeFileSync(output+'/example-analysis.json',JSON.stringify(payload,null,2)+'\n');
 fs.writeFileSync(output+'/example-report.html',report);
 fs.writeFileSync(output+'/evaluation.json',JSON.stringify(e,null,2)+'\n');
 console.log('PASS: packaged API, twelve assets, source-frame contract, synchronized context, 30 vials, 10 recipes, five changed decisions, evaluation rendering/network failure and HTML/JSON export');
})().catch(e=>{console.error(e);process.exitCode=1});
