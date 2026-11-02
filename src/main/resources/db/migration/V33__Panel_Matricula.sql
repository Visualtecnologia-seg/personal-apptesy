-- Acessos do painel identificados por matrícula (o "opus" é a 01)
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS panel_matricula INT;
WITH ord AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY CASE WHEN username = 'painel:opus' THEN 0 ELSE 1 END, id) AS n
  FROM user_ WHERE username LIKE 'painel:%'
)
UPDATE user_ u SET panel_matricula = ord.n, name = 'Matrícula ' || LPAD(ord.n::text, 2, '0')
FROM ord WHERE u.id = ord.id AND u.panel_matricula IS NULL;
