import {ProfessionalModel} from "./ProfessionalModel";
import {AddressModel, CreditCardModel, UserModel} from "./UserModel";
import {ProductModel} from "./ProductModel";

export type OrderModel = {
  id?: string;
  date?: string;
  startTime?: string;
  endTime?: string;
  status?: string;
  numberOfCustomers?: number;
  totalCost?: number;
  address?: AddressModel;
  product?: ProductModel;
  professional?: ProfessionalModel;
  response?: ProfessionalModel[];
  gender?: string;
  customer?: UserModel;
  error?: string;
  isReviewed?: boolean;
  payment?: {
    cardValue?: number;
    paymentType?: string;
    card?: CreditCardModel;
  };
};
