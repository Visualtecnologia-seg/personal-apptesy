import {get, put} from "./Api";
import {ParamsModel} from "../model/ParamsModel";
import {FinanceDataModel} from "../model/FinanceDataModel";

export const putFinancePay = async (id?: number) => {
  return await put("/finances/" + id + "/pay");
};

export const getProfessionalsFinanceData = async (params?: ParamsModel, filter?: FinanceDataModel) => {
  return await get("/finances/data");
};