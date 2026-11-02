const datasetSummary = document.querySelector('#dataset-summary');
const runRows = document.querySelector('#run-rows');
const datasetLoadStatus = document.querySelector('#dataset-load-status');

fetch('/api/runs')
  .then(response => response.ok ? response.json() : Promise.reject(new Error('run manifest unavailable')))
  .then(renderRuns)
  .catch(error => {
    datasetLoadStatus.textContent = 'Manifest unavailable';
    datasetLoadStatus.classList.add('load-error');
    const row = document.createElement('tr');
    const cell = document.createElement('td');
    cell.colSpan = 6;
    cell.textContent = error.message;
    row.append(cell);
    runRows.replaceChildren(row);
  });

function renderRuns(payload) {
  datasetSummary.replaceChildren();
  runRows.replaceChildren();
  addDatasetMetric(payload.runCount, 'Supplied runs');
  addDatasetMetric(payload.cameraStreamCount, 'Video streams');
  addDatasetMetric(payload.totalVials, 'Total vials');
  addDatasetMetric(payload.labeledVials, 'Recipe-labeled');
  addDatasetMetric(payload.unlabeledVials, 'Unlabeled');

  payload.runs.forEach(run => {
    const row = document.createElement('tr');
    addRunCell(row, `Run ${run.videoId}`);
    addRunCell(row, formatDuration(run.durationSeconds));
    addRunCell(row, run.vialCount);
    addRunCell(row, run.cameraStreams.map(cameraLabel).join(', '));
    addRunCell(row, run.recipeLabeled ? `${run.vialCount} labeled` : 'Not supplied');

    const statusCell = document.createElement('td');
    const status = document.createElement('span');
    status.className = 'run-status available';
    status.textContent = run.analysisStatus === 'AVAILABLE_NOT_VERIFIED'
      ? 'Replay installed, not yet verified'
      : run.analysisStatus === 'DATA_UNAVAILABLE'
        ? 'Restricted replay data not installed'
        : 'Inventory only, no recipe labels';
    statusCell.append(status);
    row.append(statusCell);
    runRows.append(row);
  });

  datasetLoadStatus.textContent = 'Dataset inventory loaded';
  datasetLoadStatus.classList.add('load-ok');
}

function addDatasetMetric(value, label) {
  const item = document.createElement('div');
  const number = document.createElement('strong');
  const caption = document.createElement('span');
  number.textContent = value;
  caption.textContent = label;
  item.append(number, caption);
  datasetSummary.append(item);
}

function addRunCell(row, value) {
  const cell = document.createElement('td');
  cell.textContent = value;
  row.append(cell);
}

function formatDuration(seconds) {
  const minutes = Math.floor(seconds / 60);
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`;
}

function cameraLabel(value) {
  if (value === 'cap_seal') return 'Cap/seal';
  if (value === 'tank_level') return 'Tank level';
  if (value === 'vial_fill') return 'Vial fill';
  return value;
}
