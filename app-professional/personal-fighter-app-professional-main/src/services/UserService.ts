import {get, post, put} from "./Api";
import {UserRecordsModel} from "../model/UserRecordsModel";
import * as SecureStore from "expo-secure-store";

export const getUser = async (): Promise<any> => {
  return await get("/auth/mobile");
};

export const updateUser = async (data: any): Promise<any> => {
  const response = await put("/users", data);
  await SecureStore.setItemAsync("personal-user", JSON.stringify(response.data));
  return response;
};

export const updateUserPassword = async ({password, newPassword}): Promise<any> => {
  let data = {password, newPassword};
  return await put("/auth/new-password", data);
};

export const saveRecords = async (data: UserRecordsModel): Promise<any> => {
  return await post("/users/records", data);
};