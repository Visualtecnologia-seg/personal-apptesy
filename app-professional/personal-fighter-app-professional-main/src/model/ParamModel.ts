import {OrderModel} from "./OrderModel";
import {UserModel} from "./UserModel";
import {ProfessionalModel} from "./ProfessionalModel";
import {ProductModel} from "./ProductModel";

export type ParamModel = {
  order?: OrderModel,
  user?: UserModel,
  professional?: ProfessionalModel,
  product?: ProductModel,
}