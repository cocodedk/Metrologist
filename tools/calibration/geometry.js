/* Ground truth in millimetres, calculated independently of the app solver. */
(() => {
  const distance = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);
  function metrics(points) {
    const edge = points.map((p, i) => distance(p, points[(i + 1) % 4]));
    const area = Math.abs(points.reduce((s, p, i) => {
      const q = points[(i + 1) % 4]; return s + p[0] * q[1] - q[0] * p[1];
    }, 0)) / 2;
    const angles = points.map((p, i) => {
      const a = points[(i + 3) % 4].map((v, j) => v - p[j]);
      const b = points[(i + 1) % 4].map((v, j) => v - p[j]);
      const dot = (a[0] * b[0] + a[1] * b[1]) / Math.hypot(...a) / Math.hypot(...b);
      return Math.acos(Math.max(-1, Math.min(1, dot))) * 180 / Math.PI;
    });
    return { width: (edge[0] + edge[2]) / 2, height: (edge[1] + edge[3]) / 2,
      area, diagonal: (distance(points[0], points[2]) + distance(points[1], points[3])) / 2, angles };
  }
  function inside(p, polygon) {
    return polygon.every((a, i) => {
      const b = polygon[(i + 1) % 4];
      return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]) > 0;
    });
  }
  function make({ width, height, length, stickWidth, scenario }) {
    if (![width, height, length, stickWidth].every(v => Number.isFinite(v) && v > 0)) {
      throw new Error('Enter finite, positive dimensions for the target and reference.');
    }
    if (width < 40 || height < 30 || width > 2000 || height > 2000 || stickWidth >= length) {
      throw new Error('Use a target of at least 4 × 3 cm and a reference longer than it is wide.');
    }
    const points = scenario === 'quad'
      ? [[0, 0], [width * 5 / 6, 0], [width, height * 10 / 11], [0, height]]
      : [[0, 0], [width, 0], [width, height], [0, height]];
    const x = (width - length) / 2, y = height * .65;
    const reference = [[x, y], [x + length, y], [x + length, y + stickWidth], [x, y + stickWidth]];
    if (!reference.every(p => inside(p, points))) throw new Error('The reference must fit entirely inside the target.');
    return { version: 2, scenario, width, height, length, stickWidth, points, reference, margin: 12, truth: metrics(points) };
  }
  function compare(target, measured, tolerance) {
    if (!(Number.isFinite(tolerance) && tolerance > 0 && tolerance <= 20)) throw new Error('Tolerance must be above 0 and at most 20%.');
    return Object.entries(measured).map(([key, value]) => {
      if (!(key in target.truth) || !Number.isFinite(value) || value <= 0) throw new Error('Enter finite, positive app measurements.');
      const expected = target.truth[key], error = (value - expected) / expected * 100;
      return { key, expected, measured: value, errorPercent: error, passed: Math.abs(error) <= tolerance };
    });
  }
  globalThis.TargetGeometry = { make, metrics, compare };
  if (typeof module !== 'undefined') module.exports = globalThis.TargetGeometry;
})();
