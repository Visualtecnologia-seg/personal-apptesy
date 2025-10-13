import {UserModel} from "./UserModel";

export type BankDataModel = {
  id?: string;
  bank?: string;
  agency?: string;
  account?: string;
  accountType?: string;
  pixEmail?: string;
  pixPhoneNumber?: string;
  pixCpf?: string;
  professional?: UserModel;
}