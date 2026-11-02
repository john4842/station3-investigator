const decisionStatus = document.querySelector('#decision-status');
const findingFirst = document.querySelector('#finding-first');
const findingLater = document.querySelector('#finding-later');
const findingStable = document.querySelector('#finding-stable');
const findingSensitive = document.querySelector('#finding-sensitive');
const contextTimestamp = document.querySelector('#context-timestamp');
const contextJoin = document.querySelector('#context-join');
const contextTiming = document.querySelector('#context-timing');
const contextContract = document.querySelector('#context-contract');

function renderExecutive(payload, cameraEvidenceAvailable) {
  try {
    const recipe7 = payload.assessments.find(item => item.recipeId === 7);
    if (!recipe7 || payload.provenanceStatus !== 'VERIFIED_CANONICAL_SOURCE') {
      throw new Error('verified Recipe 7 evidence unavailable');
    }

    findingFirst.textContent = formatDelta(recipe7.withinBatchMetrics?.vial1ToVial3DeltaE00);
    findingLater.textContent = formatDelta(recipe7.laterPairDeltaE00);
    findingStable.textContent = payload.assessments
      .filter(item => item.status === 'WITHIN_RUN_STABLE').length;
    findingSensitive.textContent = payload.assessments
      .filter(item => item.status === 'THRESHOLD_SENSITIVE').length;
    const context = payload.processContext;
    const contract = payload.extractionReceipt?.sourceFrameContract;
    if (!context?.crossCamera || !context?.timingModel || !contract) {
      throw new Error('verified process context unavailable');
    }
    contextTimestamp.textContent = context.crossCamera.timestamp;
    contextJoin.textContent = `${context.crossCamera.views.length} camera indexes agree on this Run 5 timestamp.`;
    contextTiming.textContent = `Component-aware fit: R² ${context.timingModel.perTankVolumesAndActiveComponentCountR2.toFixed(3)}. Total-volume-only fit: R² ${context.timingModel.totalVolumeOnlyR2.toFixed(3)}. Same-run description, not prediction.`;
    contextContract.textContent = `${contract.frameCount} indexed fill frames verified by contract: ${contract.inFillFrames} in-fill and ${contract.betweenFillFrames} between fills.`;
    decisionStatus.textContent = 'Run 5 checked';
    decisionStatus.classList.add('verified');
    document.querySelector('#canonical-brief').hidden = false;
    document.querySelector('#canonical-evidence').hidden = !cameraEvidenceAvailable;
    if (cameraEvidenceAvailable) {
      document.querySelectorAll('img[data-src]').forEach(image => { image.src = image.dataset.src; });
    }
  } catch (error) {
    findingFirst.textContent = 'Unavailable';
    findingLater.textContent = 'Unavailable';
    findingStable.textContent = 'Unavailable';
    findingSensitive.textContent = 'Unavailable';
    decisionStatus.textContent = error.message;
    contextTimestamp.textContent = 'Unavailable';
    contextJoin.textContent = 'Process context unavailable.';
    contextTiming.textContent = 'Timing context unavailable.';
    contextContract.textContent = 'Source contract unavailable.';
    decisionStatus.classList.add('failed');
    document.querySelector('#canonical-brief').hidden = true;
    document.querySelector('#canonical-evidence').hidden = true;
  }
}

function formatDelta(value) {
  return Number.isFinite(value) ? value.toFixed(3) : 'Unavailable';
}
