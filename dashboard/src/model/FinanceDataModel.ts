export interface FinanceDataModel {
  id?: number,
  name?: string,
  surname?: string,
  avatarUrl?: string,
  cpf?: string,
  email?: string,
  phoneNumber?: string,
  active?: boolean,

  customerPaymentProfile?: string,
  professionalBalance?: number,

  pixPhoneNumber?: string,
  pixEmail?: string,
  pixCpf?: string,
}
