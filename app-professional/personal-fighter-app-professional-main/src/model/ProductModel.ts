import {UserModel} from "./UserModel";

export type ProductModel = {
  id?: string;
  name?: string;
  about?: string;
  equipment?: string;
  professional?: UserModel
}