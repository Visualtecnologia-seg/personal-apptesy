import {get, patch, post, put} from "./Api";
import {ParamsModel} from "../model/ParamsModel";
import {UserModel} from "../model/UserModel";
import {resizeFile} from "../utils/FileUtils";
import {FinanceDataModel} from "../model/FinanceDataModel";

export const getUser = async (id: string) => {
  return get("/users/" + id);
};

export const getCustomers = async (params?: ParamsModel, filter?: UserModel) => {
  let reqParams = {...params, ...filter};
  return await get("/users", reqParams);
};

export const getProfessionals = async (params?: ParamsModel, filter?: UserModel) => {
  let reqParams = {...params, ...filter};
  return await get("/users", reqParams);
};

export const putUser = async (data?: UserModel) => {
  return await put("/users", data);
};

export const enableOrDisableUser = async (id?: string) => {
  return patch("/users/active/" + id);
};

export const imageUpload = async (f: any) => {
  let file = f[0];

  file = await resizeFile(file);

  if (file.size >= 1048576) {
    return "Error";
  }

  const data = new FormData();
  data.append("file", file, "file.jpg");
  return await post("/users/upload-avatar", data);
};