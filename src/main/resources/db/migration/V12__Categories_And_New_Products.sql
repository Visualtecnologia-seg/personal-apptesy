-- V12: Expand categories, assign all products, add new professions

-- 0. Reset sequences to avoid duplicate key conflicts from manual inserts in V2
SELECT setval(pg_get_serial_sequence('category_', 'id'), GREATEST((SELECT MAX(id) FROM category_), 1), true);
SELECT setval(pg_get_serial_sequence('product_', 'id'), GREATEST((SELECT MAX(id) FROM product_), 1), true);

-- 1. Add icon column to category_
ALTER TABLE category_ ADD COLUMN IF NOT EXISTS icon VARCHAR(16) DEFAULT '🔧';
ALTER TABLE category_ ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 99;

-- 2. Update existing "Artes Marciais" category
UPDATE category_ SET name = 'Artes Marciais & Luta', icon = '🥊', sort_order = 1 WHERE id = 1;

-- 3. Insert new categories (ids auto-generated)
INSERT INTO category_ (name, icon, sort_order, active) VALUES
  ('Fitness & Saúde',         '🏋️', 2,  true),
  ('Elétrica & Hidráulica',   '⚡', 3,  true),
  ('Construção & Reformas',   '🏗️', 4,  true),
  ('Marcenaria & Carpintaria','🪚', 5,  true),
  ('Jardinagem & Área Verde', '🌿', 6,  true),
  ('Limpeza & Conservação',   '🧹', 7,  true),
  ('Serviços de Urgência',    '🔑', 8,  true),
  ('Transporte & Cargas',     '🚛', 9,  true),
  ('Pets & Animais',          '🐾', 10, true),
  ('Cuidados com Pessoas',    '💆', 11, true),
  ('Beleza & Estética',       '💅', 12, true),
  ('Eventos & Arte',          '🎉', 13, true),
  ('Gastronomia',             '🍽️', 14, true),
  ('Educação & Coaching',     '📚', 15, true),
  ('Tecnologia & Digital',    '💻', 16, true),
  ('Serviços Profissionais',  '⚖️', 17, true);

-- 4. Assign existing products to categories
-- cat 1 = Artes Marciais & Luta (already set for ids 1-5)
UPDATE product_ SET category_id = 1 WHERE name IN ('MUAY-THAI','BOXE','JIU-JITSU','KARATE','MMA','Instrutor de Muay Thai','Instrutor de Boxe','Instrutor de Jiu-Jitsu');

-- cat 2 = Fitness & Saúde
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Fitness & Saúde')
  WHERE name IN ('Personal Trainer','Instrutor de Yoga','Instrutor de Pilates','Instrutor de Natação',
                 'Instrutor de Crossfit','Instrutor de Dança','Preparador Físico',
                 'Treinador de Futebol','Árbitro Esportivo');

-- cat 3 = Elétrica & Hidráulica
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Elétrica & Hidráulica')
  WHERE name IN ('Eletricista','Encanador','Técnico em Refrigeração','Instalador de Ar-condicionado');

-- cat 4 = Construção & Reformas
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Construção & Reformas')
  WHERE name IN ('Pedreiro','Pintor','Gesseiro','Azulejista','Serralheiro','Dedetizador',
                 'Instalador de Piso','Instalador de Telhado','Instalador de Forro',
                 'Instalador de Persiana','Cortineiro','Marmorista','Impermeabilizador',
                 'Reformas em Geral','Limpeza Pós-Obra','Montador de Móveis','Vidraceiro');

-- cat 5 = Marcenaria & Carpintaria
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Marcenaria & Carpintaria')
  WHERE name IN ('Carpinteiro');

-- cat 6 = Jardinagem & Área Verde
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Jardinagem & Área Verde')
  WHERE name IN ('Jardineiro');

-- cat 7 = Limpeza & Conservação
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Limpeza & Conservação')
  WHERE name IN ('Limpeza Residencial','Limpeza de Piscina','Limpeza de Calhas',
                 'Limpeza de Fachada','Higienização de Estofados');

-- cat 8 = Serviços de Urgência
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Serviços de Urgência')
  WHERE name IN ('Técnico em Alarme','Instalador de Câmeras');

-- cat 9 = Transporte & Cargas
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Transporte & Cargas')
  WHERE name IN ('Motorista Particular','Motoboy','Segurança Pessoal');

-- cat 10 = Pets & Animais
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Pets & Animais')
  WHERE name IN ('Dog Walker','Banho e Tosa','Pet Sitter','Adestrador de Cães','Tosador(a)','Veterinário(a)');

-- cat 11 = Cuidados com Pessoas
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Cuidados com Pessoas')
  WHERE name IN ('Fisioterapeuta','Massoterapeuta','Nutricionista','Enfermeiro(a)',
                 'Cuidador de Idosos','Psicólogo(a)','Terapeuta Ocupacional',
                 'Acupunturista','Podólogo(a)');

-- cat 12 = Beleza & Estética
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Beleza & Estética')
  WHERE name IN ('Cabeleireiro(a)','Barbeiro','Manicure e Pedicure','Maquiador(a)',
                 'Designer de Sobrancelhas','Depilador(a)','Esteticista',
                 'Colorista Capilar','Lash Designer','Micropigmentador(a)','Nail Designer');

-- cat 13 = Eventos & Arte
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Eventos & Arte')
  WHERE name IN ('Fotógrafo','DJ','DJ para Festas','Bartender','Garçom',
                 'Decorador(a) de Eventos','Animador(a) Infantil','Cantor(a)',
                 'Músico(a)','Mágico(a)');

-- cat 14 = Gastronomia
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Gastronomia')
  WHERE name IN ('Chef a Domicílio','Confeiteiro(a)');

-- cat 15 = Educação & Coaching
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Educação & Coaching')
  WHERE name IN ('Professor Particular','Professor de Inglês','Professor de Espanhol',
                 'Professor de Francês','Coach de Vida','Personal Organizer');

-- cat 16 = Tecnologia & Digital
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Tecnologia & Digital')
  WHERE name IN ('Suporte em Informática','Programador(a)','Designer Gráfico',
                 'Editor de Vídeo','Social Media','Especialista em SEO','Técnico em Energia Solar');

-- cat 17 = Serviços Profissionais
UPDATE product_ SET category_id = (SELECT id FROM category_ WHERE name = 'Serviços Profissionais')
  WHERE name IN ('Advogado(a)','Consultor Financeiro','Contador(a)','Redator(a)','Tradutor(a)');

-- 5. Add new professions requested by user
INSERT INTO product_ (name, about, active, category_id) VALUES
  ('Marceneiro',               'Fabricação e montagem de móveis e estruturas em madeira sob medida.',                                            true, (SELECT id FROM category_ WHERE name = 'Marcenaria & Carpintaria')),
  ('Poceiro / Perfurador de Poços', 'Perfuração, instalação e manutenção de poços artesianos e semi-artesianos.',                              true, (SELECT id FROM category_ WHERE name = 'Construção & Reformas')),
  ('Chaveiro',                 'Abertura de fechaduras, cópia de chaves, instalação e reparo de segredos.',                                     true, (SELECT id FROM category_ WHERE name = 'Serviços de Urgência')),
  ('Desentupidor',             'Desentupimento de pias, vasos sanitários, ralos e tubulações.',                                                 true, (SELECT id FROM category_ WHERE name = 'Serviços de Urgência')),
  ('Coletor de Entulho',       'Coleta, transporte e descarte de entulho, resíduos de obra e materiais volumosos.',                            true, (SELECT id FROM category_ WHERE name = 'Transporte & Cargas')),
  ('Limpador de Canil',        'Limpeza e higienização de canis, gatis e ambientes de animais.',                                               true, (SELECT id FROM category_ WHERE name = 'Pets & Animais')),
  ('Roçador / Operador de Roçadeira', 'Roçagem de terrenos, capina e limpeza de áreas com mato usando roçadeira.',                            true, (SELECT id FROM category_ WHERE name = 'Jardinagem & Área Verde')),
  ('Lenhador / Poda de Árvores','Corte, poda, retirada e processamento de árvores e galhos de forma segura.',                                 true, (SELECT id FROM category_ WHERE name = 'Jardinagem & Área Verde'));
