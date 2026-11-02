'use strict';
fetch('/api/evaluation').then(r => {
  if (!r.ok) throw new Error('Comparison unavailable');
  return r.json();
}).then(data => {
  if (!Array.isArray(data.rows) || data.rows.length !== data.denominator) throw new Error('Incomplete comparison');
  const fragment = document.createDocumentFragment();
  for (const row of data.rows) {
    const tr = document.createElement('tr');
    for (const value of [row.recipeId, row.firstToLaterMeanDeltaE00, row.laterPairDeltaE00, row.baseline, row.guarded]) {
      const td = document.createElement('td');
      td.textContent = value == null ? 'Unavailable' : typeof value === 'number' && !Number.isInteger(value) ? value.toFixed(3) : String(value);
      tr.appendChild(td);
    }
    fragment.appendChild(tr);
  }
  document.querySelector('#evaluation-rows').replaceChildren(fragment);
  document.querySelector('#evaluation-status').textContent = data.changedDecisions + ' of ' + data.denominator + ' decisions change. These are measured decision differences, not an accuracy improvement.';
}).catch(() => {
  document.querySelector('#evaluation-rows').replaceChildren();
  document.querySelector('#evaluation-status').textContent = 'Comparison unavailable. No results are shown.';
});

