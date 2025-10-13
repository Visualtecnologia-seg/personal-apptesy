export interface UserModel {
  id?: string,
  username?: string,
  name?: string,
  surname?: string,
  birthday?: string,
  age?: string,
  cpf?: string,
  gender?: string,
  phoneNumber?: string,
  email?: string,
  avatarUrl?: string,
  professional?: ProfessionalModel,
  customer?: CustomerModel,
  active?: boolean,
  role?: string[] | "ADMIN" | "CUSTOMER" | "PROFESSIONAL",
}

export interface ProfessionalModel {
  id?: string,
  about?: string,
  resume?: string,
  reference?: string,
  price?: number,
  rating?: number,
  count?: number,
  active?: boolean
}

export interface CustomerModel {
  id?: string,
  active?: boolean
}
