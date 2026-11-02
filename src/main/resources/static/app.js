const form = document.querySelector('#analysis-form');
const canonicalSubmit = document.querySelector('#canonical-submit');
const uploadSubmit = document.querySelector('#upload-submit');
const message = document.querySelector('#message');
const results = document.querySelector('#results');
const summary = document.querySelector('#summary');
const receipt = document.querySelector('#receipt');
const rows = document.querySelector('#assessment-rows');
const health = document.querySelector('#health');
const download = document.querySelector('#download');
const downloadHtml = document.querySelector('#download-html');
const resultContext = document.querySelector('#result-context');
const sourceBundle = document.querySelector('#sourceBundle');
const sourceBundleName = document.querySelector('#source-bundle-name');
let latestResult = null;

sourceBundle.addEventListener('change', () => {
  const file = sourceBundle.files[0];
  sourceBundleName.textContent = file ? `${file.name} · ${formatFileSize(file.size)}` : 'No file selected';
  sourceBundleName.classList.toggle('selected', Boolean(file));
});

fetch('/api/health')
  .then(response => response.ok ? response.json() : Promise.reject(new Error('unavailable')))
  .then(() => setHealth('Ready', 'ok'))
  .catch(() => setHealth('Unavailable', 'error'));

canonicalSubmit.addEventListener('click', async () => {
  canonicalSubmit.disabled = true;
  clearResult();
  setMessage('Verifying saved measurements and computing recipe comparisons…');
  try {
    const response = await fetch('/api/analyze/canonical', { method: 'POST' });
    await handleResponse(response);
  } catch (error) {
    showAnalysisError(error);
  } finally {
    canonicalSubmit.disabled = false;
  }
});

form.addEventListener('submit', async event => {
  event.preventDefault();
  uploadSubmit.disabled = true;
  clearResult();
  setMessage('Inspecting the uploaded frames…');
  try {
    const response = await fetch('/api/analyze', { method: 'POST', body: new FormData(form) });
    await handleResponse(response);
  } catch (error) {
    showAnalysisError(error);
  } finally {
    uploadSubmit.disabled = false;
  }
});

// Show the verified replay on first load. The button remains available for a manual refresh.
canonicalSubmit.click();

function clearResult() {
  latestResult = null;
  results.hidden = true;
}

function showAnalysisError(error) {
  clearResult();
  const detail = error instanceof TypeError
    ? 'Cannot reach the analysis service. Check the connection and try again.'
    : error.message || 'Analysis failed. Try again.';
  setMessage(detail, 'error');
}

async function handleResponse(response) {
  try {
    const payload = await response.json();
    if (!response.ok) throw new Error(payload.message || 'Analysis failed');
    render(payload);
    latestResult = payload;
    const disposition = payload.provenanceStatus === 'VERIFIED_CANONICAL_SOURCE' ? 'verified' : 'unverified';
    setMessage(`Done. ${payload.measurementRows} vial measurements and ${payload.assessmentCount} recipe results, ${disposition}.`, 'success');
  } catch (error) {
    showAnalysisError(error);
  }
}

download.addEventListener('click', () => {
  if (!latestResult) return;
  const blob = new Blob([JSON.stringify(latestResult, null, 2)], { type: 'application/json' });
  const anchor = document.createElement('a');
  anchor.href = URL.createObjectURL(blob);
  anchor.download = 'station3-analysis.json';
  anchor.click();
  URL.revokeObjectURL(anchor.href);
});

downloadHtml.addEventListener('click', () => {
  if (!latestResult) return;
  const blob = new Blob([htmlReport(latestResult)], { type: 'text/html' });
  const anchor = document.createElement('a');
  anchor.href = URL.createObjectURL(blob);
  anchor.download = 'station3-analysis.html';
  anchor.click();
  URL.revokeObjectURL(anchor.href);
});

function render(payload) {
  summary.replaceChildren();
  rows.replaceChildren();
  resultContext.textContent = `${payload.datasetLabel} · ${formatTimestamp(payload.generatedAt)}`;
  addMetric(payload.manifestRows, 'Manifest rows');
  addMetric(payload.measurementRows, 'Vials measured');
  addMetric(payload.assessmentCount, 'Recipes checked');
  addMetric(evidenceTypeLabel(payload.evidenceType), 'Evidence type');
  renderReceipt(payload.extractionReceipt);
  document.querySelector('#investigation-details').innerHTML = investigationHtml(payload);

  payload.assessments.forEach(assessment => {
    const row = document.createElement('tr');
    addCell(row, assessment.recipeId);
    addCell(row, observationQualityLabel(assessment));
    addStatusCell(row, assessment.status);
    addCell(row, humanize(assessment.batchConsistency));
    addCell(row, humanize(assessment.recipeConformity));
    addCell(row, assessment.acceptedMeasurements);
    addCell(row, formatNumber(assessment.withinBatchMetrics?.vial1ToVial2DeltaE00));
    addCell(row, formatNumber(assessment.withinBatchMetrics?.vial1ToVial3DeltaE00));
    addCell(row, formatNumber(assessment.withinBatchMetrics?.vial2ToVial3DeltaE00));
    addCell(row, formatNumber(assessment.firstToLaterMeanDeltaE00));
    addCell(row, formatNumber(assessment.predecessorDeltaE00));
    addCell(row, assessment.withinBatchMetrics?.referenceSource === 'NONE' ? 'Unavailable' : 'Vial 3 is provisional');
    rows.append(row);
  });
  results.hidden = false;

}

function renderReceipt(value) {
  receipt.replaceChildren();
  if (!value) { receipt.hidden = true; return; }
  receipt.hidden = false;
  const title = document.createElement('strong');
  title.textContent = 'Run record';
  const detail = document.createElement('span');
  detail.textContent = `Saved Run 5 measurements · ${value.acceptedVials} accepted, ${value.rejectedVials} rejected · source ${shortHash(value.sourceVideoSha256)} · extractor ${shortHash(value.extractorSha256)}`;
  receipt.append(title, detail);
}

function addMetric(value, label) {
  const card = document.createElement('div');
  card.className = 'metric';
  const strong = document.createElement('strong');
  const caption = document.createElement('span');
  strong.textContent = value;
  caption.textContent = label;
  card.append(strong, caption);
  summary.append(card);
}

function addCell(row, value) {
  const cell = document.createElement('td');
  cell.textContent = value ?? 'Not available';
  row.append(cell);
}

function addStatusCell(row, status) {
  const cell = document.createElement('td');
  const badge = document.createElement('span');
  badge.className = `status-badge ${statusClass(status)}`;
  badge.textContent = statusLabel(status);
  badge.title = `API status: ${status}`;
  cell.append(badge);
  row.append(cell);
}

function statusClass(status) {
  if (status === 'WITHIN_RUN_STABLE') return 'stable';
  if (status === 'RETROSPECTIVE_FIRST_VIAL_DEVIATION') return 'deviation';
  if (status === 'THRESHOLD_SENSITIVE') return 'sensitive';
  return 'insufficient';
}

function statusLabel(status) {
  if (status === 'WITHIN_RUN_STABLE') return 'No first-vial shift';
  if (status === 'RETROSPECTIVE_FIRST_VIAL_DEVIATION') return 'First-vial shift';
  if (status === 'THRESHOLD_SENSITIVE') return 'Needs review';
  if (status === 'INSUFFICIENT_PROVENANCE') return 'Unverified source';
  if (status === 'INSUFFICIENT_REFERENCE_EVIDENCE') return 'Insufficient evidence';
  return humanize(status);
}

function evidenceTypeLabel(value) {
  if (value === 'CANONICAL_VIDEO_5_REPLAY') return 'Verified replay';
  if (value === 'UNVERIFIED_UPLOAD') return 'Unverified upload';
  return 'Unknown';
}

function observationQualityLabel(assessment) {
  return assessment.acceptedMeasurements === assessment.expectedVials
    ? 'Accepted'
    : `${assessment.acceptedMeasurements} of ${assessment.expectedVials} accepted`;
}

function formatNumber(value) { return Number.isFinite(value) ? value.toFixed(3) : 'Not available'; }
function shortHash(value) { return value ? `${value.slice(0, 10)}…` : 'missing'; }
function formatFileSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
function formatTimestamp(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Analysis complete' : date.toLocaleString();
}
function humanize(value) { return String(value ?? 'Not available').toLowerCase().replaceAll('_', ' '); }
function setHealth(text, state) {
  health.querySelector('span:last-child').textContent = text;
  health.classList.remove('ok', 'error');
  health.classList.add(state);
}
function setMessage(text, state = '') {
  message.textContent = text;
  message.classList.remove('success', 'error');
  if (state) message.classList.add(state);
}

function htmlReport(payload) {
  const bodyRows = payload.assessments.map(item => `<tr><td>${item.recipeId}</td><td>${escapeHtml(statusLabel(item.status))}</td><td>${escapeHtml(humanize(item.batchConsistency))}</td><td>${escapeHtml(humanize(item.recipeConformity))}</td><td>${item.acceptedMeasurements}/${item.expectedVials}</td><td>${formatNumber(item.withinBatchMetrics?.vial1ToVial2DeltaE00)}</td><td>${formatNumber(item.withinBatchMetrics?.vial1ToVial3DeltaE00)}</td><td>${formatNumber(item.withinBatchMetrics?.vial2ToVial3DeltaE00)}</td><td>${formatNumber(item.firstToLaterMeanDeltaE00)}</td><td>${formatNumber(item.predecessorDeltaE00)}</td><td>${escapeHtml((item.rejectionReasons || []).join('; '))}</td></tr>`).join('');
  const evidence = payload.extractionReceipt || {};
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Station 3 evidence report</title><style>body{font:15px system-ui;max-width:1300px;margin:40px auto;color:#172b42}table{width:100%;border-collapse:collapse}th,td{padding:9px;border:1px solid #ccd5df;text-align:left}th{background:#f3f6f9}code{word-break:break-all}.note{padding:12px;background:#fff8e8}</style></head><body><h1>Station 3 evidence report</h1><p class="note">Retrospective evidence only. The visual pattern supports investigating carryover; it does not prove carryover. Vial 3 is a provisional dataset-specific reference, not independent recipe truth.</p><p><strong>mode:</strong> ${escapeHtml(evidence.replayMode || payload.evidenceType)}</p><p><strong>analysis policy:</strong> ${escapeHtml(payload.analysisPolicyVersion || 'not supplied')} — ${escapeHtml(payload.thresholdInterpretation || 'not supplied')}</p><p><strong>source video sha-256:</strong> <code>${escapeHtml(evidence.sourceVideoSha256 || 'not verified')}</code></p><p><strong>manifest sha-256:</strong> <code>${escapeHtml(evidence.manifestSha256 || 'not verified')}</code></p><p><strong>extractor sha-256:</strong> <code>${escapeHtml(evidence.extractorSha256 || 'not verified')}</code></p><p><strong>evidence:</strong> ${evidence.acceptedVials ?? 0} accepted, ${evidence.rejectedVials ?? 0} rejected</p><table><thead><tr><th>recipe</th><th>status</th><th>batch consistency</th><th>recipe conformity</th><th>valid vials</th><th>vial 1–2 ΔE00</th><th>vial 1–3 ΔE00</th><th>vial 2–3 ΔE00</th><th>vial 1 vs later mean ΔE00</th><th>predecessor ΔE00</th><th>reasons</th></tr></thead><tbody>${bodyRows}</tbody></table>${investigationHtml(payload)}</body></html>`;
}

function escapeHtml(value) {
  return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
}


function investigationHtml(payload) {
  const evidence = payload.investigation;
  if (!evidence) return '<p>Recipe process context is unavailable for this source.</p>';
  const recipeText = recipe => recipe
    ? 'Recipe ' + recipe.recipeId + ': T100 = ' + recipe.requestedMl.T100 + ' mL; T200 = ' + recipe.requestedMl.T200 + ' mL; T400 = ' + recipe.requestedMl.T400 + ' mL. Dispense order: ' + recipe.dispenseOrder + '.'
    : 'None in this recording.';
  const contexts = evidence.recipeContexts.map(context => {
    const a = payload.assessments.find(item => item.recipeId === context.recipeId);
    const vials = evidence.vials.filter(v => v.recipeId === context.recipeId);
    return '<details><summary>Recipe ' + context.recipeId + ' · ' + escapeHtml(statusLabel(a.status)) + '</summary>'
      + '<p>' + escapeHtml(recipeText(context.recipe)) + '</p>'
      + '<p>Immediate predecessor: ' + escapeHtml(recipeText(context.predecessorRecipe)) + '</p>'
      + '<p>' + escapeHtml(a.explanation) + '</p>'
      + '<p>Process evidence: ' + escapeHtml(humanize(context.processContextConfidence)) + '. ' + escapeHtml(context.hypothesis) + '</p>'
      + '<p>Action: ' + escapeHtml(context.actionRecommendation) + '</p>'
      + '<p>Rejected evidence: ' + escapeHtml((a.rejectionReasons || []).join('; ') || 'None') + '</p>'
      + '<table><thead><tr><th>Physical vial</th><th>Fill interval (s)</th><th>Quality</th><th>Measured CIELAB</th><th>Source reference</th></tr></thead><tbody>'
      + vials.map(v => '<tr><td>' + v.vial + '</td><td>' + formatNumber(v.startSeconds) + '–' + formatNumber(v.endSeconds)
        + '</td><td>' + escapeHtml(v.observationQuality) + '</td><td>' + escapeHtml(JSON.stringify(v.appearance))
        + '</td><td>' + escapeHtml(v.sourceReference || 'Unavailable') + '</td></tr>').join('')
      + '</tbody></table></details>';
  }).join('');
  return '<h3>Recipe investigation</h3><p>' + escapeHtml(evidence.volumeInterpretation) + '</p><p>'
    + escapeHtml(evidence.tankColorContext) + '</p>' + contexts
    + '<details><summary>Demonstration limits</summary><ul>'
    + Object.entries(evidence.capabilities).map(([key,value]) => '<li>' + escapeHtml(key.replace(/([A-Z])/g, ' $1')) + ': ' + escapeHtml(humanize(value)) + '</li>').join('')
    + '</ul></details>';
}

