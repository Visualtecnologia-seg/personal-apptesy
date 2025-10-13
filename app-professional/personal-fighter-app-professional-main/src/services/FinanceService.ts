import {get, post} from "./Api";
import {UserRecordsModel} from "../model/UserRecordsModel";
import {BankDataModel} from "../model/BankDataModel";

export const getFinance = async (id: string): Promise<any> => {
  return await get("/finances/users/" + id);
};

export const saveFinance = async (data: UserRecordsModel): Promise<any> => {
  return await post("/finances", data);
};

export const getBankData = async (id: string): Promise<any> => {
  return await get("/bank/users/" + id);
};

export const saveBankData = async (data: BankDataModel): Promise<any> => {
  return await post("/bank", data);
};