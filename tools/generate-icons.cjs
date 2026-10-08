/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');

async function main() {
  const source = path.join(root, 'bluej/icons/bluej-light.svg');
  const bird = fs.readFileSync(path.join(root, 'bluej/icons/bluej-bird-official.png')).toString('base64');
  const svg = Buffer.from(fs.readFileSync(source, 'utf8').replace('bluej-bird-official.png', `data:image/png;base64,${bird}`));
  const sizes = [16, 24, 32, 48, 64, 128, 256, 512];
  const images = [];
  for (const size of sizes) {
    const png = await sharp(svg).resize(size, size).png().toBuffer();
    fs.writeFileSync(path.join(root, `bluej/icons/bluej-light-${size}.png`), png);
    fs.writeFileSync(path.join(root, `bluej/lib/images/bluej-icon-${size}.png`), png);
    if (size <= 256) images.push({size, png});
    if (size === 48 || size === 256) {
      fs.writeFileSync(path.join(root, `bluej/package/debianfiles/icons/hicolor/${size}x${size}/apps/bluej.png`), png);
    }
  }
  const header = Buffer.alloc(6 + images.length * 16);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(images.length, 4);
  let offset = header.length;
  images.forEach(({size, png}, i) => {
    const entry = 6 + i * 16;
    header[entry] = size === 256 ? 0 : size;
    header[entry + 1] = size === 256 ? 0 : size;
    header.writeUInt16LE(1, entry + 4);
    header.writeUInt16LE(32, entry + 6);
    header.writeUInt32LE(png.length, entry + 8);
    header.writeUInt32LE(offset, entry + 12);
    offset += png.length;
  });
  const ico = Buffer.concat([header, ...images.map(item => item.png)]);
  fs.writeFileSync(path.join(root, 'bluej/icons/bluej-light.ico'), ico);
  fs.writeFileSync(path.join(root, 'bluej/package/winlaunch/bluej-vista.ico'), ico);
  fs.writeFileSync(path.join(root, 'bluej/icons/bluej-vista.ico'), ico);
}
main().catch(error => { console.error(error); process.exitCode = 1; });
