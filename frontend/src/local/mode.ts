/**
 * Modo local: o app roda SEM servidor. Os dados ficam no navegador de quem usa (localStorage) e um
 * "motor" local responde no lugar da API. Ativado com `--mode standalone` (ver .env.standalone).
 * É o que permite publicar uma versão pública, que começa vazia e onde cada visitante tem os seus próprios dados.
 */
export const STANDALONE: boolean = import.meta.env.VITE_STANDALONE === 'true'

/** Build para página incorporada (artefato): o tema é do visualizador, então o app não oferece seletor de tema. */
export const EMBEDDED: boolean = import.meta.env.VITE_EMBED === 'true'
