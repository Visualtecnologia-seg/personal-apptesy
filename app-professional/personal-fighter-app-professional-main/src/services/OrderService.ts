import {get, put} from "./Api";
import {OrderModel} from "../model";
import {ReqParamsModel} from "../model/ReqParamsModel";

export const getOrdersByStatus = async (status: string, id: string, params: ReqParamsModel, filters?: ReqParamsModel): Promise<any> => {
  const reqParams = params ? params : filters;
  return await get("/orders/professionals/" + id + "/" + status, reqParams);
};

export const getOrdersByProfessional = async (id: string, params: ReqParamsModel): Promise<any> => {
  return await get("/orders/professionals/" + id, params);
};


export const confirmOrder = async (data: OrderModel, id: string): Promise<any> => {
  return await put("/orders/professionals/" + id + "/confirmation", data);
};

export const declineOrder = async (data: OrderModel, id: string): Promise<any> => {
  return await put("/orders/professionals/" + id + "/rejection", data);
};

export const cancelOrder = async (data: OrderModel, id?: string): Promise<any> => {
  return await put("/orders/professionals/" + id + "/cancellation", data);
};