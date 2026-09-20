async (page) => {
  const ensure = (condition, message) => { if (!condition) throw new Error(message); };
  const checks = [];
  await page.setViewportSize({ width: 1600, height: 1000 });
  await page.reload();
  ensure(await page.locator('#photo').isDisabled(), 'Uncalibrated screen must not enter photo mode');
  ensure(await page.locator('#truth-area').innerText() === '345.00 cm²', 'Default rectangle area');
  await page.getByRole('button', { name: 'Match a card or ruler' }).click();
  await page.locator('#method').selectOption('ruler');
  // UI simulation only: this does not physically calibrate the user's monitor.
  await page.locator('#scale-range').fill('300');
  await page.getByRole('button', { name: 'The reference matches' }).click();
  const bounds = await page.locator('#target svg').boundingBox();
  ensure(Math.abs(bounds.width - 254 * 3) < .01, 'SVG must use the confirmed CSS-pixels/mm');
  ensure(Math.abs(bounds.height - 174 * 3) < .01, 'SVG aspect and physical scale');
  ensure(await page.locator('#photo').isEnabled(), 'A calibrated fitting target permits photo mode');
  await page.locator('#photo').click();
  const photoBounds = await page.locator('#target svg').boundingBox();
  ensure(Math.abs(bounds.width - photoBounds.width) < .01, 'Photo mode must never resize the target');
  await page.keyboard.press('Escape');
  checks.push('Calibration and unchanged physical dimensions in photo mode');
  await page.locator('#scenario').selectOption('quad');
  const corners = await page.locator('#target svg metadata').textContent();
  const nonrectangle = JSON.parse(corners);
  ensure(nonrectangle.truth.angles.some(a => Math.abs(a - 90) > 10), 'Nonrectangle must retain non-right angles');
  ensure(nonrectangle.reference[1][0] - nonrectangle.reference[0][0] === 100, 'Reference length stays fixed across cases');
  checks.push('Independent nonrectangle ground truth and fixed reference dimensions');
  await page.locator('#scenario').selectOption('front');
  await page.locator('summary').click();
  await page.locator('#actual-width').fill('23');
  await page.locator('#actual-height').fill('15');
  await page.locator('#actual-area').fill('345');
  await page.locator('#compare').click();
  ensure(await page.locator('#result .pass').count() === 3, 'Exact synthetic entries must pass');
  await page.locator('#actual-width').fill('25');
  ensure(await page.locator('#record').isDisabled(), 'Editing measured values invalidates the old record');
  await page.locator('#compare').click();
  ensure(await page.locator('#result .fail').count() === 1, 'Wrong width must visibly fail');
  await page.locator('#width').fill('0');
  ensure(await page.locator('#error').isVisible(), 'Invalid target must show correction');
  ensure(await page.locator('#target').isHidden(), 'Invalid settings cannot leave a misleading old target');
  ensure(await page.locator('#record').isDisabled(), 'Target edits invalidate old comparison');
  await page.locator('#width').fill('23');
  await page.locator('summary').click();
  checks.push('Comparison, failure display and stale-record invalidation');
  await page.screenshot({ path: 'output/playwright/calibration-checked-desktop.png' });
  await page.evaluate(() => {
    Object.defineProperty(window, 'devicePixelRatio', { value: 2, configurable: true });
    window.dispatchEvent(new Event('resize'));
  });
  ensure(await page.locator('#photo').isDisabled(), 'Changed pixel scale must require rechecking');
  checks.push('Zoom/display scale change invalidates calibration');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  ensure(await page.locator('#photo').isDisabled(), 'A saved scale must not be auto-confirmed on reload');
  await page.locator('#fit').click();
  const mobile = await page.evaluate(() => ({ body: document.documentElement.scrollWidth,
    viewport: innerWidth, target: document.querySelector('#target svg').getBoundingClientRect().width,
    stage: document.querySelector('#stage').clientWidth }));
  ensure(mobile.body <= mobile.viewport, 'Mobile layout must not overflow horizontally');
  ensure(mobile.target < mobile.stage, 'Explicit fit must fit the target');
  await page.screenshot({ path: 'output/playwright/calibration-mobile.png', fullPage: true });
  checks.push('Mobile layout, explicit fitting and calibration confirmation after reload');
  return { kind: 'Browser UI simulation; no real-camera measurement', checks };
}
