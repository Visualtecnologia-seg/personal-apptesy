import {ProfessionalModel} from "./ProfessionalModel";
import {UserModel} from "./UserModel";
import {OrderModel} from "./OrderModel";

export type ReviewModel = {
  id?: string,
  professional?: ProfessionalModel,
  customer?: UserModel,
  order?: OrderModel,
  rating?: number;
  comment?: string;
  date?: string;
};
