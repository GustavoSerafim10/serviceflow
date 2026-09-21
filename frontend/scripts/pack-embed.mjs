// Empacota o build `--mode embed` (dist-embed/) numa ÚNICA página HTML autocontida: dist-embed/pagina.html.
//
// A saída é um "fragmento": título + <style> + <div id="root"> + <script>. Sem <html>/<head>/<body>, porque a
// plataforma de publicação (artefatos) envolve o fragmento no seu próprio esqueleto.
// Para hospedar como site comum (GitHub Pages, Netlify...), use `npm run build:local` (pasta dist-local/).
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'

const dir = fileURLToPath(new URL('../dist-embed/', import.meta.url))
const css = readFileSync(join(dir, 'assets', 'app.css'), 'utf8')
const js = readFileSync(join(dir, 'assets', 'app.js'), 'utf8')

// Dentro de <script>, a sequência "</script" encerraria a tag antes da hora.
const safeJs = js.replaceAll('</script', '<\\/script').replaceAll('<!--', '<\\!--')

const html = `<title>ServiceFlow Chamados</title>
<style>${css}</style>
<div id="root"></div>
<script>${safeJs}</script>
`

writeFileSync(join(dir, 'pagina.html'), html)
console.log(`pagina.html gerado: ${(html.length / 1024).toFixed(0)} KB`)
