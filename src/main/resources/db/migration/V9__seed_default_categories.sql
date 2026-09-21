-- V9__seed_default_categories.sql
--
-- Categorias iniciais. Os nomes coincidem com os rótulos que o serviço de
-- sugestão (Python) foi treinado para prever; assim a sugestão de categoria
-- funciona logo na primeira execução. O ADMIN pode renomear, desativar ou criar
-- outras a qualquer momento (a API casa o rótulo sugerido com as categorias
-- ATIVAS pelo nome, ignorando maiúsculas/minúsculas).
--
-- O NOT EXISTS torna a migration segura em bancos que já tenham categorias
-- com esses nomes (ex: "Rede" criada manualmente): nada é duplicado.

INSERT INTO categories (name, description)
SELECT v.name, v.description
FROM (VALUES
    ('Rede',           'Conectividade, Wi-Fi, VPN, firewall e infraestrutura de rede'),
    ('Hardware',       'Computadores, notebooks, monitores, periféricos e servidores'),
    ('Software',       'Sistemas, aplicativos, instalações e atualizações'),
    ('Acesso e Senha', 'Contas, senhas, permissões e autenticação'),
    ('E-mail',         'Caixa de entrada, envio e recebimento, calendário e listas'),
    ('Impressora',     'Impressoras, scanners, toner e filas de impressão')
) AS v(name, description)
WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE lower(c.name) = lower(v.name));
