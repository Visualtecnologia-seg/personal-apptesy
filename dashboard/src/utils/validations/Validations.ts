import {phoneMask} from "../Masks";

export const cpfOrCnpj = (document: any) => {
  if (document !== undefined) {
    if (document.length === 14) {
      return cpfValidation(document);
    } else if (document.length === 18) {
      return cnpjValidation(document);
    } else {
      return false;
    }
  }
  return false;
};

export const inputCheck = (model: any, data: any) => {
  if (model !== undefined) {
    if (data !== undefined) {
      if (data !== null) {
        return data;
      } else {
        return data;
      }
    }
  }
};

export const phoneMaskCheck = (model: any, phone: any) => {
  if (model !== undefined) {
    if (phone !== undefined) {
      if (phone !== null) {
        if (phone.indexOf("0") === 0) {
          phone = phone.replace("0", "");
        }
        return phoneMask(phone);
      }
    } else {
      return phone;
    }
  } else {
    return "";
  }
};

export const cnpjValidation = (cnpj: any) => {
  if (cnpj !== undefined) {
    cnpj = cnpj.replace(/[^\d]+/g, "");

    // Elimina CNPJs invalidos conhecidos
    if (cnpj.split("").every((char: any) => char === cnpj[0])) {
      return false;
    }

    // Valida DVs
    let tamanho = cnpj.length - 2;
    let numeros = cnpj.substring(0, tamanho);
    let digitos = cnpj.substring(tamanho);
    let soma = 0;
    let pos = tamanho - 7;
    for (let i = tamanho; i >= 1; i--) {
      soma += numeros.charAt(tamanho - i) * pos--;
      if (pos < 2) pos = 9;
    }
    let resultado = soma % 11 < 2 ? 0 : 11 - (soma % 11);
    if (resultado !== digitos.charAt(0)) return false;

    tamanho = tamanho + 1;
    numeros = cnpj.substring(0, tamanho);
    soma = 0;
    pos = tamanho - 7;
    for (let i = tamanho; i >= 1; i--) {
      soma += numeros.charAt(tamanho - i) * pos--;
      if (pos < 2) pos = 9;
    }
    resultado = soma % 11 < 2 ? 0 : 11 - (soma % 11);
    if (resultado !== digitos.charAt(1)) return false;

    return true;
  }
  return false;
};

export const cpfValidation = (strCPF: any) => {
  if (strCPF !== undefined) {
    strCPF = strCPF
      .replace(".", "")
      .replace(".", "")
      .replace("-", "");

    var Soma;
    var Resto;
    Soma = 0;
    if (strCPF == "00000000000") return false;

    for (var i = 1; i <= 9; i++) Soma = Soma + parseInt(strCPF.substring(i - 1, i)) * (11 - i);
    Resto = (Soma * 10) % 11;

    if ((Resto == 10) || (Resto == 11)) Resto = 0;
    if (Resto != parseInt(strCPF.substring(9, 10))) return false;

    Soma = 0;
    for (i = 1; i <= 10; i++) Soma = Soma + parseInt(strCPF.substring(i - 1, i)) * (12 - i);
    Resto = (Soma * 10) % 11;

    if ((Resto == 10) || (Resto == 11)) Resto = 0;
    if (Resto != parseInt(strCPF.substring(10, 11))) return false;
    return true;
  }
};

export const birthDateValidation = (date: any) => {
  if (date !== undefined && date !== null) {
    if (date !== "") {
      const yearBirth = parseInt(date.substring(0, date.indexOf("-")));
      const actualYear = new Date().getFullYear();

      if (actualYear - yearBirth < 18 || actualYear - yearBirth > 150) {
        return false;
      }
      return true;
    }
    return false;
  }
  return false;
};

export const dateValidation = (date: any) => {
  if (date !== undefined) {
    const yearBirth = parseInt(date.substring(0, date.indexOf("-")));
    const actualYear = new Date().getFullYear();

    if (actualYear < yearBirth) {
      return false;
    }
    return true;
  }
  return false;
};
