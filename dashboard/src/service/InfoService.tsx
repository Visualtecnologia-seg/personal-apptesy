import {get, post, remove} from "./Api";
import {ParamsModel} from "../model/ParamsModel";
import {InfoModel} from "../model/InfoModel";

export const getInfos = async (params?: ParamsModel) => {
  return get("/infos", params);
};

export const postInfo = async (data?: InfoModel) => {
  return post("infos", data);
};

export const removeInfo = async (id?: string) => {
  return remove("/infos/" + id);
};
