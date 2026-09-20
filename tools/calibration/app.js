(() => {
  const $ = id => document.getElementById(id), geometry = TargetGeometry;
  const inputs = ['width', 'height', 'length', 'stick-width'];
  let ppm = 96 / 25.4, calibrated = false, target = null, record = null, signature = '';
  const screenSignature = () => [devicePixelRatio, screen.width, screen.height, window.visualViewport?.scale || 1].join(':');
  try { const saved = JSON.parse(localStorage.getItem('metrologist-target-v2')); if (saved?.ppm > 0 && saved.ppm < 30) ppm = saved.ppm; } catch (_) { /* Storage is optional. */ }
  const notes = {
    front: 'Start with the phone facing the screen. A valid frontal rectangle should give a usable result.',
    angle: 'Keep this target flat. Move the phone to one side, then above or below it; compare with the same ground truth.',
    quad: 'This shape has non-right angles. The app must preserve them or explain why it cannot measure this view. Inputs set its bounding box.',
  };
  function invalidateRecord() { record = null; $('record').disabled = true; $('result').replaceChildren(); }
  function setScaleStatus() {
    $('scale-status').textContent = calibrated ? `Screen scale checked · ${ppm.toFixed(3)} px/mm` : 'Preview only · calibration needed';
    $('badge').textContent = calibrated ? 'Screen scale checked' : 'Preview scale';
    $('scale-status').classList.toggle('ready', calibrated); $('badge').classList.toggle('ready', calibrated);
  }
  function fitStatus() {
    if (!target) return;
    const fits = (target.width + 24) * ppm <= $('stage').clientWidth - 8 && (target.height + 24) * ppm <= $('stage').clientHeight - 8;
    $('stage-message').hidden = fits;
    $('stage-message').textContent = 'The complete target does not fit. Use “Fit target and reference”, a larger window, or smaller dimensions.';
    $('photo').disabled = !calibrated || !fits;
  }
  function draw() {
    invalidateRecord(); $('case-note').textContent = notes[$('scenario').value];
    try {
      target = geometry.make({ width: +$('width').value * 10, height: +$('height').value * 10,
        length: +$('length').value * 10, stickWidth: +$('stick-width').value * 10, scenario: $('scenario').value });
      $('target').innerHTML = TargetDisplay.svg(target);
      const image = $('target').firstElementChild;
      image.style.width = `${(target.width + 24) * ppm}px`; image.style.height = `${(target.height + 24) * ppm}px`;
      document.documentElement.style.setProperty('--print-width', `${target.width + 24}mm`);
      document.documentElement.style.setProperty('--print-height', `${target.height + 24}mm`);
      $('target-title').textContent = target.scenario === 'quad' ? 'Non-rectangle' : 'Rectangle';
      for (const key of ['width', 'height', 'diagonal', 'area']) $('truth-' + key).textContent =
        `${(target.truth[key] / (key === 'area' ? 100 : 10)).toFixed(2)} ${key === 'area' ? 'cm²' : 'cm'}`;
      $('angles').textContent = `Corner angles: ${target.truth.angles.map(a => a.toFixed(2) + '°').join(' · ')}`;
      $('error').hidden = true; $('target').hidden = false; $('svg').disabled = false; $('print').disabled = false;
      fitStatus();
    } catch (error) {
      target = null; $('error').textContent = error.message; $('error').hidden = false; $('target').hidden = true;
      $('stage-message').hidden = true; $('photo').disabled = true; $('svg').disabled = true; $('print').disabled = true;
      for (const key of ['width', 'height', 'diagonal', 'area']) $('truth-' + key).textContent = '—';
      $('angles').textContent = '';
    }
    setScaleStatus();
  }
  function sample() {
    const card = $('method').value === 'card', width = +$('scale-range').value;
    $('sample').classList.toggle('ruler', !card); $('sample').style.width = width + 'px';
    $('sample').style.height = (card ? width * 53.98 / 85.6 : 24) + 'px';
    $('sample').textContent = card ? '85.6 mm' : '100 mm';
    $('scale-value').textContent = `${width.toFixed(1)} CSS pixels`;
    $('confirm').disabled = width > $('sample-space').clientWidth;
  }
  function beginCalibration() {
    $('calibration').showModal();
    $('scale-range').max = Math.min(900, $('sample-space').clientWidth - 4);
    $('scale-range').value = ppm * ($('method').value === 'card' ? 85.6 : 100); sample();
  }
  $('calibrate').onclick = beginCalibration;
  $('method').onchange = () => { $('scale-range').value = ppm * ($('method').value === 'card' ? 85.6 : 100); sample(); };
  $('scale-range').oninput = sample;
  for (const [id, step] of [['minus', -.5], ['plus', .5]]) $(id).onclick = () => { $('scale-range').value = +$('scale-range').value + step; sample(); };
  $('confirm').onclick = () => {
    ppm = +$('scale-range').value / ($('method').value === 'card' ? 85.6 : 100);
    calibrated = true; signature = screenSignature();
    try { localStorage.setItem('metrologist-target-v2', JSON.stringify({ ppm })); } catch (_) { /* Scale still works without persistence. */ }
    $('calibration').close(); draw(); $('calibrate').focus();
  };
  for (const id of [...inputs, 'scenario']) $(id).addEventListener('input', () => { $('fit-note').textContent = ''; draw(); });
  $('fit').onclick = () => {
    if (!target) return;
    const factor = Math.min(1, (($('stage').clientWidth - 12) / ppm - 24) / target.width,
      (($('stage').clientHeight - 12) / ppm - 24) / target.height);
    if (!(factor > 0)) return;
    for (const id of inputs) $(id).value = (Math.floor(+$(id).value * factor * 100) / 100).toFixed(2);
    draw(); $('fit-note').textContent = 'Dimensions updated explicitly. Enter the new reference length and width in the app.';
  };
  function photo(on) {
    document.body.classList.toggle('photo', on); $('exit-photo').hidden = !on;
    requestAnimationFrame(fitStatus); if (on) $('exit-photo').focus(); else $('photo').focus();
  }
  $('photo').onclick = () => photo(true); $('exit-photo').onclick = () => photo(false);
  addEventListener('keydown', e => { if (e.key === 'Escape' && !$('calibration').open) photo(false); });
  $('fullscreen').onclick = async () => {
    try { if (document.fullscreenElement) await document.exitFullscreen(); else await document.documentElement.requestFullscreen(); }
    catch (_) { $('fit-note').textContent = 'Use your browser’s fullscreen control instead.'; }
  };
  function resized() {
    if (calibrated && signature !== screenSignature()) { calibrated = false; invalidateRecord(); setScaleStatus(); }
    fitStatus();
  }
  addEventListener('resize', resized); window.visualViewport?.addEventListener('resize', resized);
  new ResizeObserver(fitStatus).observe($('stage'));
  $('svg').onclick = () => target && TargetDisplay.download('metrologist-target.svg', TargetDisplay.svg(target), 'image/svg+xml');
  $('print').onclick = () => {
    if (target && (target.width + 24 > 259 || target.height + 24 > 180)) {
      $('fit-note').textContent = 'For A4 or Letter landscape printing, reduce the target to at most 23.5 × 15.6 cm. Print scaling must stay at 100%.';
    } else window.print();
  };
  $('compare').onclick = () => {
    invalidateRecord();
    try {
      if (!calibrated || !target) throw new Error('Check the screen scale and target dimensions first.');
      const measured = { width: +$('actual-width').value * 10, height: +$('actual-height').value * 10 };
      if ($('actual-area').value !== '') measured.area = +$('actual-area').value * 100;
      const tolerance = +$('tolerance').value, results = geometry.compare(target, measured, tolerance);
      $('result').innerHTML = '<table><thead><tr><th>Measurement</th><th>Error</th><th>Check</th></tr></thead><tbody>' +
        results.map(r => `<tr><td>${r.key}</td><td>${r.errorPercent.toFixed(2)}%</td><td class="${r.passed ? 'pass' : 'fail'}">${r.passed ? 'Within tolerance' : 'Outside tolerance'}</td></tr>`).join('') + '</tbody></table>';
      record = { recordedAt: new Date().toISOString(), target, screenPixelsPerMm: ppm, tolerancePercent: tolerance,
        results, inputSource: 'Manual entry from app; screen scale confirmed by operator' };
      $('record').disabled = false;
    } catch (error) { $('result').textContent = error.message; }
  };
  for (const id of ['actual-width', 'actual-height', 'actual-area', 'tolerance']) $(id).addEventListener('input', invalidateRecord);
  $('record').onclick = () => record && TargetDisplay.download('metrologist-test-record.json', JSON.stringify(record, null, 2), 'application/json');
  draw();
})();
