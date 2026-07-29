import fs from 'node:fs';
import path from 'node:path';

const clientDir = path.resolve(process.cwd(), 'dist/client');
const assetsDir = path.join(clientDir, 'assets');

if (fs.existsSync(assetsDir)) {
  const files = fs.readdirSync(assetsDir);
  const jsFile = files.find(f => f.startsWith('index-') && f.endsWith('.js')) || files.find(f => f.endsWith('.js'));
  const cssFile = files.find(f => f.startsWith('styles-') && f.endsWith('.css')) || files.find(f => f.endsWith('.css'));

  const htmlContent = `<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>AnimeBox - Stream Anime for Android</title>
    <meta name="description" content="Official AnimeBox app landing page for Android." />
    ${cssFile ? `<link rel="stylesheet" href="/Animebox/assets/${cssFile}">` : ''}
    ${cssFile ? `<link rel="stylesheet" href="./assets/${cssFile}">` : ''}
  </head>
  <body class="bg-[#09090b] text-white">
    <div id="root"></div>
    ${jsFile ? `<script type="module" src="/Animebox/assets/${jsFile}"></script>` : ''}
  </body>
</html>`;

  fs.writeFileSync(path.join(clientDir, 'index.html'), htmlContent);
  fs.writeFileSync(path.join(clientDir, '404.html'), htmlContent);
  fs.writeFileSync(path.join(clientDir, '.nojekyll'), '');
  console.log('Successfully generated static index.html, 404.html, and .nojekyll for GitHub Pages!');
}
