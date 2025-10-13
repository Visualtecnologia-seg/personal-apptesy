export const cpfMask = (value: any) => {
  return value
    .replace(/\D/g, "")
    .replace(/(\d{3})(\d)/, "$1.$2")
    .replace(/(\d{3})(\d)/, "$1.$2")
    .replace(/(\d{3})(\d{1,2})/, "$1-$2")
    .replace(/(-\d{2})\d+?$/, "$1");
};

export const cnpjMask = (value: string) => {
  return value
    .replace(/\D/g, "")
    .replace(/(\d{2})(\d)/, "$1.$2")
    .replace(/(\d{3})(\d)/, "$1.$2")
    .replace(/(\d{3})(\d)/, "$1/$2")
    .replace(/(\d{4})(\d)/, "$1-$2")
    .replace(/(-\d{2})\d+?$/, "$1");
};

export const phoneMask = (value: string) => {
  return value
    .replace(/\D/g, "")
    .replace(/(\d{2})(\d)/, "($1) $2")
    .replace(/(\d{4})(\d)/, "$1-$2")
    .replace(/(\d{4})-(\d)(\d{4})/, "$1$2-$3")
    .replace(/(-\d{4})\d+?$/, "$1");
};

export const cepMask = (value: string) => {
  return value
    .replace(/\D/g, "")
    .replace(/(\d{5})(\d)/, "$1-$2")
    .replace(/(-\d{3})\d+?$/, "$1");
};

export const pisMask = (value: string) => {
  return value
    .replace(/\D/g, "")
    .replace(/(\d{3})(\d)/, "$1.$2")
    .replace(/(\d{5})(\d)/, "$1.$2")
    .replace(/(\d{5}\.)(\d{2})(\d)/, "$1$2-$3")
    .replace(/(-\d{1})\d+?$/, "$1");
};

export function currencyMask(value: any) {
  value = value + "";
  value = parseInt(value.replace(/[\D]+/g, ""));
  value = value + "";
  value = value.replace(/([0-9]{2})$/g, ".$1");

  if (value.length > 6) {
    value = value.replace(/([0-9]{3}),([0-9]{2}$)/g, ",$1,$2");
  }

  return value;
}

export function currencyMaskValue(value: any) {
  value = value + "";
  value = parseFloat(value.replace(/[\D]+/g, ""));
  ;
  value = value + "";

  if (!isNaN(value)) {
    value = value.replace(/([0-9]{2})$/g, ",$1");
    if (value.length > 6) {
      value = value.replace(/([0-9]{3}),([0-9]{2}$)/g, ".$1,$2");
    }
    return value;
  } else {
    return 0;
  }
}
