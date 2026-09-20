(() => {
  function svg(target) {
    const t = target, m = t.margin, w = t.width + 2 * m, h = t.height + 2 * m;
    const [x, y] = t.reference[0], label = `${t.length.toFixed(1)} × ${t.stickWidth.toFixed(1)} mm`;
    const cross = (p, i) => `<path d="M${p[0] - 3},${p[1]}h6 M${p[0]},${p[1] - 3}v6" stroke="#111" stroke-width=".35"/>
      <circle cx="${p[0]}" cy="${p[1]}" r=".55" fill="#111"/>
      <text x="${p[0] + (i === 0 || i === 3 ? -5 : 5)}" y="${p[1] + (i < 2 ? -5 : 7)}" text-anchor="middle" font-size="3.5">${i + 1}</text>`;
    const border = Math.min(t.length, t.stickWidth) / 10, cell = border / 2;
    const innerLength = t.length - 2 * border;
    const bands = Array.from({ length: 5 }, (_, i) => `<rect x="${x + border + i * innerLength / 5}" y="${y + border}" width="${innerLength / 5}" height="${t.stickWidth - 2 * border}" fill="${i % 2 ? '#fff' : '#f00'}"/>`).join('');
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}mm" height="${h}mm" role="img" aria-label="${t.scenario === 'quad' ? 'Nonrectangular' : 'Rectangular'} measurement target with numbered corners and a red-ended reference with a checker border on all four sides">
      <title>Metrologist test target</title><desc>All coordinates are millimetres. Reference ${label}.</desc>
      <metadata>${JSON.stringify(t)}</metadata><rect width="${w}" height="${h}" fill="#eeeee8"/>
      <g transform="translate(${m},${m})" fill="#111" font-family="sans-serif">
        <polygon points="${t.points.map(p => p.join(',')).join(' ')}" fill="#fff" stroke="#111" stroke-width=".3"/>
        ${t.points.map(cross).join('')}
        <defs><pattern id="checker" x="${x}" y="${y}" width="${border}" height="${border}" patternUnits="userSpaceOnUse">
          <rect width="${border}" height="${border}" fill="#fff"/><path d="M0 0h${cell}v${cell}H0zM${cell} ${cell}h${cell}v${cell}H${cell}z" fill="#000"/>
        </pattern><clipPath id="bar-clip"><rect x="${x}" y="${y}" width="${t.length}" height="${t.stickWidth}"/></clipPath></defs>
        <g clip-path="url(#bar-clip)"><rect x="${x}" y="${y}" width="${t.length}" height="${t.stickWidth}" fill="url(#checker)"/>${bands}<rect x="${x}" y="${y}" width="${t.length}" height="${t.stickWidth}" fill="none" stroke="#111" stroke-width=".5"/></g>
        <text x="${x + t.length / 2}" y="${y - 4}" text-anchor="middle" font-size="3">REFERENCE · ${label}</text>
        <text x="${t.width / 2}" y="${t.height + 8}" text-anchor="middle" font-size="2.7">Mark crosshair centres · checker border outer corners</text>
      </g></svg>`;
  }
  function download(name, body, type) {
    const link = document.createElement('a'), url = URL.createObjectURL(new Blob([body], { type }));
    link.href = url; link.download = name; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  globalThis.TargetDisplay = { svg, download };
})();
