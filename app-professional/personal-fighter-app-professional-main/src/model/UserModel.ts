export interface UserModel {
  id?: string,
  username?: string,
  password?: string,
  confirmPassword?: string,
  name?: string,
  surname?: string,
  birthday?: string,
  cpf?: string,
  gender?: string,
  phoneNumber?: string,
  email?: string,
  avatarUrl?: string,
  customer?: CustomerModel,
  professional?: ProfessionalModel,
  role?: string[]
}

/* Professional Interfaces */
export interface ProfessionalModel {
  id?: string,
  about?: string,
  resume?: string,
  price?: number,
  rating?: number,
  count?: number,
  active?: boolean,
  reference?: string;
}

/* Customer Interfaces */
export interface CustomerModel {
  id?: string,
  active?: boolean,
}

export interface AddressModel {
  id?: string;
  street?: string;
  number?: string;
  complement?: string;
  neighborhood?: string;
  city?: string;
  state?: string;
  zipcode?: string;
  active?: boolean
}

export interface CreditCardModel {
  id?: string,
  lastNumbers?: string,
  name?: string,
  street?: string,
  streetNumber?: string,
  neighborhood?: string,
  zipcode?: string,
  city?: string,
  state?: string,
  country?: string,
}

export interface NewCreditCardModel {
  id?: string,
  card_number?: string,
  card_expiration_date?: string,
  card_holder_name?: string,
  card_cvv?: string,
  billing?: {
    street?: string,
    street_number?: string,
    neighborhood?: string,
    zipcode?: string,
    city?: string,
    state?: string,
    country?: string,
  }
}