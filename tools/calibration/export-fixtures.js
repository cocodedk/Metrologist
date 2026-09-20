async (page) => {
  // Render the actual page artwork at 3 px/mm. Intrinsics and gravity below are
  // synthetic: these images exercise marking/measurement, not a physical camera.
  await page.setViewportSize({ width: 1600, height: 1000 });
  await page.reload();
  await page.locator('#calibrate').click();
  await page.locator('#method').selectOption('ruler');
  await page.locator('#scale-range').fill('300');
  await page.locator('#confirm').click();
  const fixtures = [];
  for (const [name, scenario] of [['frontal', 'front'], ['nonrectangle', 'quad']]) {
    await page.locator('#scenario').selectOption(scenario);
    const target = JSON.parse(await page.locator('#target svg metadata').textContent());
    const bounds = await page.locator('#target svg').boundingBox();
    if (bounds.width !== 762 || bounds.height !== 522) throw new Error('Unexpected target scale');
    // Element screenshots round fractional clip edges outward. Align the artwork
    // itself to whole pixels so its PNG and declared coordinate origin agree.
    await page.locator('#target svg').scrollIntoViewIfNeeded();
    await page.locator('#target svg').evaluate(svg => {
      const b = svg.getBoundingClientRect();
      svg.style.transform = `translate(${Math.round(b.x) - b.x}px, ${Math.round(b.y) - b.y}px)`;
    });
    await page.locator('#target svg').screenshot({
      path: `app/src/androidTest/assets/calibration-target/${name}.png`, scale: 'css',
    });
    const pixels = points => points.map(p => p.map(v => (v + target.margin) * 3));
    fixtures.push({ name, source: 'tools/calibration-target.html',
      kind: 'Rendered page with synthetic pinhole metadata; not a camera capture',
      imageWidth: 762, imageHeight: 522, intrinsics: { fx: 1200, fy: 1200, cx: 381, cy: 261 },
      physicalDown: [0, 1, 0], orientation: 'VERTICAL',
      objectPixels: pixels(target.points), referencePixels: pixels(target.reference),
      referenceLengthMetres: target.length / 1000, referenceWidthMetres: target.stickWidth / 1000,
      expected: { widthMetres: target.truth.width / 1000, heightMetres: target.truth.height / 1000,
        areaSquareMetres: target.truth.area / 1e6, diagonalMetres: target.truth.diagonal / 1000,
        anglesDegrees: target.truth.angles },
      target,
    });
  }
  return { fixtures };
}
