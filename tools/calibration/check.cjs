const assert = require('node:assert/strict');
const { make, metrics, compare } = require('./geometry.js');
const close = (a, b) => assert.ok(Math.abs(a - b) < 1e-9, `${a} != ${b}`);
const rectangle = make({ width: 240, height: 160, length: 100, stickWidth: 20, scenario: 'front' });
close(rectangle.truth.width, 240);
close(rectangle.truth.height, 160);
close(rectangle.truth.area, 38400);
close(rectangle.truth.diagonal, Math.sqrt(240 ** 2 + 160 ** 2));
rectangle.truth.angles.forEach(angle => close(angle, 90));
// Independent world fixture: (-1,-.5), (1,-.5), (1.4,.5), (-1,.6), scaled by 100 mm.
const quad = make({ width: 240, height: 110, length: 100, stickWidth: 20, scenario: 'quad' });
close(quad.truth.area, 23200);
assert.ok(quad.truth.angles.some(angle => Math.abs(angle - 90) > 15));
close(quad.truth.width, (200 + Math.hypot(240, 10)) / 2);
close(quad.truth.height, (110 + Math.hypot(40, 100)) / 2);
const turned = metrics(rectangle.points.map(([x, y]) => [-y, x]));
close(turned.area, rectangle.truth.area);
close(turned.width, rectangle.truth.width);
for (const value of [0, -1, NaN, Infinity]) {
  assert.throws(() => make({ width: value, height: 160, length: 100, stickWidth: 20, scenario: 'front' }));
}
assert.throws(() => make({ width: 240, height: 160, length: 300, stickWidth: 20, scenario: 'front' }));
const checks = compare(rectangle, { width: 242.4, height: 166.4 }, 2);
assert.equal(checks[0].passed, true);
assert.equal(checks[1].passed, false);
close(checks[0].errorPercent, 1);
close(checks[1].errorPercent, 4);
assert.throws(() => compare(rectangle, { width: Infinity }, 2));
console.log('Calibration geometry: rectangle, independent nonrectangle, rotation, rejection and error comparison passed.');
