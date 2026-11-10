-- Até agora o SMS estava em modo de teste (código fixo), então nenhum celular foi conferido de verdade.
-- Quando o SMS for ligado, cada pessoa confirma o próprio celular de novo.
UPDATE user_ SET phone_verified = FALSE WHERE phone_verified = TRUE;
