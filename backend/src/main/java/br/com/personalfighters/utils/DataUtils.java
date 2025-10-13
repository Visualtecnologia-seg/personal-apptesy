package br.com.personalfighters.utils;

import br.com.personalfighters.model.Gender;
import com.github.javafaker.Faker;
import org.apache.commons.lang3.StringUtils;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DataUtils {

  public static List<String> getNames() {
    return Arrays.asList("Gustavo", "Beatriz", "Daniel", "Davi Lucca", "Antônio", "Bryan", "Maurício", "Felipe", "Valentina", "Maria Helena", "Lorena", "João", "Enzo", "Ana Clara", "Matheus", "Isabella", "Rebeca", "Maria Luiza", "Liz", "Giovanna", "Caio", "Calebe", "Isabelly", "Vinícius", "Heitor", "Cecília", "Anthony", "Yasmin", "Vicente", "Manuela", "Ana Laura", "Gabriel", "Melissa", "Laura", "Rafaela", "Nicolas", "Marina", "Pietro", "Antonella", "Ana Luiza", "Lívia", "Bernardo", "Maitê", "Maria Clara", "Murilo", "Rafael", "Gabriela", "Júlia", "Enzo Gabriel", "Esther", "Mariana", "Sarah", "Lavínia", "Eloá", "Alícia", "Emanuelly", "Agatha", "Luiza", "Maria Cecília", "Ana Júlia", "João Pedro", "Samuel", "Miguel", "João Miguel", "Lucca", "Benjamin", "Lucas", "Lara", "Bento", "Noah", "Enrico", "Emanuel", "Pedro Henrique", "Théo", "Clara", "Davi", "Guilherme", "Eduardo", "Heloísa", "Isadora", "Leonardo", "Pedro", "Maria Eduarda", "Sophia", "Isaac", "Helena", "Cauã", "Alice", "Arthur", "Lorenzo", "Maria Júlia", "Vitor", "Elisa", "Joaquim", "João Lucas", "Henrique", "Catarina", "Isis", "Maria Alice", "Gael");
  }

  public static String getName(int i) {
    if (i < getNames().size()) {
      return getNames().get(i);
    } else {
      return getNames().get(random(getNames().size() - 1));
    }
  }

  public static String getSurname() {
    List<String> list = Arrays.asList("Rossi", "Russo", "Ferrari", "Esposito", "Bianchi", "Romano", "Colombo", "Ricci", "Marino", "Greco", "Bruno", "Gallo", "Conti", "De Luca", "Mancini", "Costa", "Giordano", "Rizzo", "Lombardi", "Moretti", "Barbieri", "Fontana", "Santoro", "Mariani", "Rinaldi", "Caruso", "Ferrara", "Galli", "Martini", "Leone", "Longo", "Gentile", "Martinelli", "Vitale", "Lombardo", "Serra", "Coppola", "De Santis", "D angelo", "Marchetti", "Parisi", "Villa", "Conte", "Ferraro", "Ferri", "Fabbri", "Bianco", "Marini", "Grasso", "Valentini", "Messina", "Sala", "De Angelis", "Gatti", "Pellegrini", "Palumbo", "Sanna", "Farina", "Rizzi", "Monti", "Cattaneo", "Morelli", "Amato", "Silvestri", "Mazza", "Testa", "Grassi", "Pellegrino", "Carbone", "Giuliani", "Benedetti", "Barone", "Rossetti", "Caputo", "Montanari", "Guerra", "Palmieri", "Bernardi", "Martino", "Fiore", "De Rosa", "Ferretti", "Bellini", "Basile", "Riva", "Donati", "Piras", "Vitali", "Battaglia", "Sartori", "Neri", "Costantini", "Milani", "Pagano", "Ruggiero", "Sorrentino", "D'amico", "Orlando", "Damico", "Negri", "Santos", "Ferreira", "Pereira", "Oliveira", "Costa", "Rodrigues", "Martins", "Jesus", "Sousa", "Fernandes", "Gonçalves", "Gomes", "Lopes", "Marques", "Alves", "Almeida", "Ribeiro", "Pinto", "Carvalho", "Teixeira", "Moreira", "Correia", "Mendes", "Nunes", "Soares", "Vieira", "Monteiro", "Cardoso", "Rocha", "Raposo", "Neves", "Coelho", "Cruz", "Cunha", "Pires", "Ramos", "Reis", "Simões", "Antunes", "Matos", "Fonseca", "Machado", "Araújo", "Barbosa", "Tavares", "Lourenço", "Castro", "Figueiredo", "Azevedo", "Freitas", "Magalhães", "Henriques", "Lima", "Guerreiro", "Batista", "Pinheiro", "Faria", "Miranda", "Barros", "Morais", "Nogueira", "Esteves", "Anjos", "Baptista", "Campos", "Mota", "Andrade", "Brito", "Sá", "Nascimento", "Leite", "Abreu", "Borges", "Melo", "Vaz", "Pinho", "Vicente", "Gaspar", "Assunção", "Maia", "Moura", "Valente", "Domingues", "Garcia", "Carneiro", "Loureiro", "Neto", "Amaral", "Branco", "Leal", "Pacheco", "Macedo", "Paiva", "Matias", "Amorim", "Torres");
    return list.get(random(list.size()));
  }

  public static String getUsername(String name, String surname, int i) {
    if (i >= getNames().size()) {
      surname = surname + "." + i;
    }
    return stripAccents(name + "." + surname).replaceAll(" ", ".").toLowerCase();
  }

  public static String getEmail(String name, int i) {
    String suffix = "";
    if (i >= getNames().size()) {
      suffix = "." + i;
    }
    return stripAccents(name + suffix).replaceAll(" ", ".").toLowerCase() + "@email.com";
  }

  public static Gender getGender(String name) {
    int index = getNames().indexOf(name);
    List<Gender> list = Arrays.asList(Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.MALE, Gender.MALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.MALE, Gender.MALE, Gender.MALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.MALE, Gender.MALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE, Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE, Gender.FEMALE, Gender.MALE);
    return list.get(index);
  }

  public static String getPhoneNumber() {
    Random rand = new Random();
    int first = Math.random() < 0.5 ? 9 : 8;
    int second = rand.nextInt(999);
    int third = rand.nextInt(9999);

    DecimalFormat df1 = new DecimalFormat("000"); // 3 zeros
    DecimalFormat df2 = new DecimalFormat("0000"); // 4 zeros

    return "+5521" + first + df1.format(second) + df2.format(third);
  }

  public static List<String> getCardNumber() {
    return Arrays.asList("5538 0859 6827 9571", "5543 0809 9280 1980", "4916 7596 8411 5724", "4556 5567 8184 6044", "4086 6795 2703 9465", "5287 9896 5483 6342", "5352 7157 0354 7790", "4539 8662 3797 0344", "4916 4141 8328 3181", "5335 8845 2955 9546", "4916 1519 7794 8344", "5194 9015 2522 3743", "4532 4704 5360 8626", "5444 3856 2967 5073", "4532 7155 6263 4370", "5421 8598 8217 7180", "4024 0071 8514 7385", "5532 5388 5667 1602", "4539 5178 6227 3872", "4929 8593 8865 1076");
  }

  public static List<String> getCardExp() {
    return Arrays.asList("0323", "0423", "0124", "0225", "0823", "1226", "0525", "0224", "0623", "0324", "0226", "0226", "0125", "0525", "1125", "0323", "0623", "0225", "1123", "1023");
  }

  public static List<String> getCardCvv() {
    return Arrays.asList("220", "242", "879", "953", "622", "675", "598", "580", "103", "571", "493", "335", "712", "389", "605", "485", "828", "177", "979", "531");
  }

  public static String getStreet() {
    Faker f = new Faker(new Locale("pt-BR"));
    return f.address().streetName();
  }

  public static String getCity() {
    Faker f = new Faker(new Locale("pt-BR"));
    return f.address().city();
  }

  public static String getState() {
    Faker f = new Faker(new Locale("pt-BR"));
    return f.address().state();
  }

  public static String getZipCode() {
    Faker f = new Faker(new Locale("pt-BR"));
    return f.address().zipCode();
  }

  public static String getBuildingNumber() {
    Faker f = new Faker(new Locale("pt-BR"));
    return f.address().buildingNumber();
  }

  public static String getSecondaryAddress() {
    Faker f = new Faker(new Locale("pt-BR"));
    return f.address().secondaryAddress();
  }

  public static String getNeighborhood() {
    List<String> list = Arrays.asList("Lagoa", "Leblon", "Ipanema", "Humaitá", "Urca", "Barra da Tijuca", "Jardim Botânico", "São Conrado", "Gávea", "Laranjeiras", "Flamengo", "Leme", "Maracanâ", "Copacabana", "Jardim Guanabara", "Botafogo", "Campo dos Afonsos", "Tijuca", "Grajaú", "Méier", "Moneró", "Cosme Velho", "Joá", "Todos os Santos", "Glória", "Andaraí", "Ribeira", "Vila Isabel", "Zumbi", "Rocha", "Praça da Bandeira", "Cachambi", "Riachuelo", "Cocotá", "Vila Valqueire", "Vila da Penha", "Catete", "Pechincha", "Freguesia (Jacarepaguá)", "Praia da Bandeira", "Maria da Graça", "São Francisco Xavier", "Anil", "Higienópolis", "Vista Alegre", "Portuguesa", "Centro", "Jardim Sulacap", "Abolição", "Rio Comprido", "Engenho Novo", "Freguesia (Ilha do Governador)", "Encantado", "Bonsucesso", "Lins de Vasconcellos", "Recreio dos Bandeirantes", "Vila Cosmos", "Engenho de Dentro", "Ramos", "Santa Teresa", "Taquara", "Irajá", "Quintino Bocaiúva", "Água Santa", "Olaria", "Piedade", "Jardim Carioca", "Bancários", "Praça Seca", "Saúde", "Tanque", "Vila Militar", "Campinho", "São Cristóvão", "Paquetá", "Penha Circular", "Oswaldo Cruz", "Jacaré", "Bento Ribeiro", "Cascadura", "Brás de Pina", "Madureira", "Pilares", "Vaz Lobo", "Engenho da Rainha", "Penha", "Cacuia", "Pitangueiras", "Marechal Hermes", "Tomás Coelho", "Sampaio", "Estácio", "Santo Cristo", "Cidade Universitária", "Cavalcanti", "Jardim América", "Tauá", "Inhaúma", "Benfica", "Catumbi", "Cordovil", "Rocha Miranda", "Coelho Neto", "Vicente de Carvalho", "Deodoro", "Cidade Nova", "Curicica", "Honório Gurgel", "Turiaçu", "Engenheiro Leal", "Del Castilho", "Galeão", "Guadalupe", "Alto da Boa Vista", "Pedra de Guaratiba", "Realengo", "Magalhães Bastos", "Colégio", "Padre Miguel", "Parque Anchieta", "Pavuna", "Gamboa", "Vidigal", "Ricardo de Albuquerque", "Bangu", "Mangueira", "Parque Columbia", "Anchieta", "Campo Grande", "Vigário Geral", "Senador Vasconcellos", "Jacarezinho", "Itanhangá", "Parada de Lucas", "Cidade de Deus", "Caju", "Gardênia Azul", "Maré", "Senador Camará", "Santíssimo", "Barros Filho", "Costa Barros", "Cosmos", "Paciência", "Inhoaíba", "Sepetiba", "Santa Cruz", "Jacarepaguá", "Complexo do Alemão", "Manguinhos", "Rocinha", "Barra de Guaratiba", "Acari", "Guaratiba", "Vargem Pequena", "Vargem Grande", "Camorim", "Grumari");
    return list.get(random(list.size()));
  }

  public static String getPhoto() {
    List<Integer> list = Arrays.asList(0, 1, 2, 3, 4, 5, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 100, 101, 102, 103, 104, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 139, 140, 141, 142, 143, 144, 145, 146, 147, 149, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202, 203, 204, 206, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 225, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 247, 248, 249, 250, 251, 252, 253, 254, 255, 256, 257, 258, 259, 260, 261, 263, 264, 265, 266, 267, 268, 269, 270, 271, 272, 273, 274, 275, 276, 277, 278, 279, 280, 281, 282, 283, 284, 287, 288, 289, 290, 291, 292, 293, 294, 295, 296, 297, 299, 300, 301, 302, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 314, 315, 316, 317, 318, 319, 320, 321, 322, 323, 324, 325, 326, 327, 328, 329, 330, 331, 334, 335, 336, 337, 338, 339, 340, 341, 342, 343, 344, 345, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 360, 361, 362, 363, 364, 365, 366, 367, 368, 369, 370, 371, 372, 373, 374, 375, 376, 377, 378, 379, 380, 381, 382, 383, 384, 385, 386, 387, 388, 389, 390, 391, 392, 393, 395, 396, 397, 398, 399, 400, 401, 402, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 415, 416, 417, 418, 419, 420, 421, 423, 424, 425, 426, 427, 428, 429, 430, 431, 432, 433, 434, 435, 436, 437, 439, 440, 441, 442, 443, 444, 445, 446, 447, 448, 449, 450, 451, 452, 453, 454, 455, 456, 457, 458, 459, 460, 461, 464, 465, 466, 467, 468, 469, 471, 472, 473, 474, 475, 476, 477, 478, 479, 480, 481, 482, 483, 484, 485, 486, 487, 488, 490, 491, 492, 493, 494, 495, 496, 497, 498, 499, 1000, 1001, 1002, 1003, 1004, 1005, 1006, 1008, 1009, 1010, 1011, 1012, 1013, 1014, 1015, 1016, 1018, 1019, 1020, 1021, 1022, 1023, 1024, 1025, 1026, 1027, 1028, 1029, 1031, 1032, 1033, 1035, 1036, 1037, 1038, 1039, 1040, 1041, 1042, 1043, 1044, 1045, 1047, 1048, 1049, 1050, 1051, 1052, 1053, 1054, 1055, 1056, 1057, 1058, 1059, 1060, 1061, 1062, 1063, 1064, 1065, 1066, 1067, 1068, 1069, 1070, 1071, 1072, 1073, 1074, 1075, 1076, 1077, 1078, 1079, 1080, 1081, 1082, 1083, 1084);
    int index = random(list.size());
    return "https://picsum.photos/id/" + list.get(index) + "/640/480";
  }

  public static LocalDate getBirthday() {
    long minDay = LocalDate.of(1960, 1, 1).toEpochDay();
    long maxDay = LocalDate.of(2000, 12, 31).toEpochDay();
    long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);
    return LocalDate.ofEpochDay(randomDay);
  }

  public static String stripAccents(String s) {
    return StringUtils.stripAccents(s.trim()).toLowerCase();
  }

  public static String getCpf() {
    int n = 9;
    int n1 = random(n);
    int n2 = random(n);
    int n3 = random(n);
    int n4 = random(n);
    int n5 = random(n);
    int n6 = random(n);
    int n7 = random(n);
    int n8 = random(n);
    int n9 = random(n);
    int d1 = n9 * 2 + n8 * 3 + n7 * 4 + n6 * 5 + n5 * 6 + n4 * 7 + n3 * 8 + n2 * 9 + n1 * 10;

    d1 = 11 - (mod(d1));
    if (d1 >= 10) d1 = 0;
    int d2 = d1 * 2 + n9 * 3 + n8 * 4 + n7 * 5 + n6 * 6 + n5 * 7 + n4 * 8 + n3 * 9 + n2 * 10 + n1 * 11;
    d2 = 11 - (mod(d2));
    if (d2 >= 10) d2 = 0;

    return "" + n1 + n2 + n3 + "." + n4 + n5 + n6 + "." + n7 + n8 + n9 + "-" + d1 + d2;
  }

  public static String getCnpj() {
    int n = 9;
    int n1 = random(n);
    int n2 = random(n);
    int n3 = random(n);
    int n4 = random(n);
    int n5 = random(n);
    int n6 = random(n);
    int n7 = random(n);
    int n8 = random(n);
    int d1 = 2 + n8 * 6 + n7 * 7 + n6 * 8 + n5 * 9 + n4 * 2 + n3 * 3 + n2 * 4 + n1 * 5;

    d1 = 11 - (mod(d1));
    if (d1 >= 10) d1 = 0;
    int d2 = d1 * 2 + 3 + n8 * 7 + n7 * 8 + n6 * 9 + n5 * 2 + n4 * 3 + n3 * 4 + n2 * 5 + n1 * 6;
    d2 = 11 - (mod(d2));
    if (d2 >= 10) d2 = 0;

    return "" + n1 + n2 + "." + n3 + n4 + n5 + "." + n6 + n7 + n8 + "/" + 0 + 0 + 0 + 1 + "-" + d1 + d2;
  }

  public static int random(int n) {
    return (int) (Math.random() * n);
  }

  private static int mod(int dividendo) {
    return (int) Math.round(dividendo - (Math.floor(dividendo / 11.0) * 11));
  }

  public static String imprimeCNPJ(String CNPJ) {
    // máscara do CNPJ: 99.999.999.9999-99
    return (CNPJ.substring(0, 2) + "." + CNPJ.substring(2, 5) + "." + CNPJ.substring(5, 8) + "." + CNPJ.substring(8, 12) + "-" + CNPJ.substring(12, 14));
  }

  public String rg(boolean comPontos) {
    String nDigResult;
    String numerosContatenados;
    String numeroGerado;
    Random numeroAleatorio = new Random();
    //numeros gerados
    int n1 = numeroAleatorio.nextInt(10);
    int n2 = numeroAleatorio.nextInt(10);
    int n3 = numeroAleatorio.nextInt(10);
    int n4 = numeroAleatorio.nextInt(10);
    int n5 = numeroAleatorio.nextInt(10);
    int n6 = numeroAleatorio.nextInt(10);
    int n7 = numeroAleatorio.nextInt(10);
    int n8 = numeroAleatorio.nextInt(10);
    int n9 = numeroAleatorio.nextInt(10);

    //Conctenando os numeros
    numerosContatenados = String.valueOf(n1) + String.valueOf(n2) + String.valueOf(n3) + String.valueOf(n4) +
        String.valueOf(n5) + String.valueOf(n6) + String.valueOf(n7) + String.valueOf(n8) +
        String.valueOf(n9);
    numeroGerado = numerosContatenados;

    if (comPontos)
      numeroGerado = "" + n1 + n2 + "." + n3 + n4 + n5 + "." + n6 + n7 + n8 + "-" + n9;
    else
      numeroGerado = "" + n1 + n2 + n3 + n4 + n5 + n6 + n7 + n8 + n9;

    return numeroGerado;
  }

  public boolean isCPF(String CPF) {
    CPF = deleteSpecialChars(CPF);

    // considera-se erro CPF's formados por uma sequencia de numeros iguais
    if (CPF.equals("00000000000") || CPF.equals("11111111111") || CPF.equals("22222222222") || CPF.equals("33333333333") || CPF.equals("44444444444") || CPF.equals("55555555555") || CPF.equals("66666666666") || CPF.equals("77777777777") || CPF.equals("88888888888") || CPF.equals("99999999999") || (CPF.length() != 11))
      return (false);

    char dig10, dig11;
    int sm, i, r, num, peso;

    // "try" - protege o codigo para eventuais erros de conversao de tipo (int)
    try {
      // Calculo do 1o. Digito Verificador
      sm = 0;
      peso = 10;
      for (i = 0; i < 9; i++) {
        // converte o i-esimo caractere do CPF em um numero:
        // por exemplo, transforma o caractere '0' no inteiro 0
        // (48 eh a posicao de '0' na tabela ASCII)
        num = (int) (CPF.charAt(i) - 48);
        sm = sm + (num * peso);
        peso = peso - 1;
      }

      r = 11 - (sm % 11);
      if ((r == 10) || (r == 11))
        dig10 = '0';
      else
        dig10 = (char) (r + 48); // converte no respectivo caractere numerico

      // Calculo do 2o. Digito Verificador
      sm = 0;
      peso = 11;
      for (i = 0; i < 10; i++) {
        num = (int) (CPF.charAt(i) - 48);
        sm = sm + (num * peso);
        peso = peso - 1;
      }

      r = 11 - (sm % 11);
      if ((r == 10) || (r == 11))
        dig11 = '0';
      else
        dig11 = (char) (r + 48);

      // Verifica se os digitos calculados conferem com os digitos informados.
      return (dig10 == CPF.charAt(9)) && (dig11 == CPF.charAt(10));
    } catch (InputMismatchException erro) {
      return (false);
    }
  }

  public boolean isCNPJ(String CNPJ) {

    CNPJ = deleteSpecialChars(CNPJ);

    // considera-se erro CNPJ's formados por uma sequencia de numeros iguais
    if (CNPJ.equals("00000000000000") || CNPJ.equals("11111111111111") || CNPJ.equals("22222222222222") || CNPJ.equals("33333333333333") || CNPJ.equals("44444444444444") || CNPJ.equals("55555555555555") || CNPJ.equals("66666666666666") || CNPJ.equals("77777777777777") || CNPJ.equals("88888888888888") || CNPJ.equals("99999999999999") || (CNPJ.length() != 14))
      return (false);

    char dig13, dig14;
    int sm, i, r, num, peso;

    // "try" - protege o código para eventuais erros de conversao de tipo (int)
    try {
      // Calculo do 1o. Digito Verificador
      sm = 0;
      peso = 2;
      for (i = 11; i >= 0; i--) {
        // converte o i-ésimo caractere do CNPJ em um número:
        // por exemplo, transforma o caractere '0' no inteiro 0
        // (48 eh a posição de '0' na tabela ASCII)
        num = (int) (CNPJ.charAt(i) - 48);
        sm = sm + (num * peso);
        peso = peso + 1;
        if (peso == 10)
          peso = 2;
      }

      r = sm % 11;
      if ((r == 0) || (r == 1))
        dig13 = '0';
      else
        dig13 = (char) ((11 - r) + 48);

      // Calculo do 2o. Digito Verificador
      sm = 0;
      peso = 2;
      for (i = 12; i >= 0; i--) {
        num = (int) (CNPJ.charAt(i) - 48);
        sm = sm + (num * peso);
        peso = peso + 1;
        if (peso == 10)
          peso = 2;
      }

      r = sm % 11;
      if ((r == 0) || (r == 1))
        dig14 = '0';
      else
        dig14 = (char) ((11 - r) + 48);

      // Verifica se os dígitos calculados conferem com os dígitos informados.
      return (dig13 == CNPJ.charAt(12)) && (dig14 == CNPJ.charAt(13));
    } catch (InputMismatchException erro) {
      return (false);
    }
  }

  private String deleteSpecialChars(String doc) {
    if (doc.contains(".")) {
      doc = doc.replace(".", "");
    }
    if (doc.contains("-")) {
      doc = doc.replace("-", "");
    }
    if (doc.contains("/")) {
      doc = doc.replace("/", "");
    }
    return doc;
  }

}
